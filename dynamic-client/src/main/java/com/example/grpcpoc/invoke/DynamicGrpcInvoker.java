package com.example.grpcpoc.invoke;

import com.example.grpcpoc.channel.GrpcChannelPool;
import com.example.grpcpoc.descriptor.DescriptorRegistry;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.util.JsonFormat;
import io.grpc.CallOptions;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class DynamicGrpcInvoker {

    private final DescriptorRegistry descriptorRegistry;
    private final GrpcChannelPool channelPool;

    public DynamicGrpcInvoker(DescriptorRegistry descriptorRegistry, GrpcChannelPool channelPool) {
        this.descriptorRegistry = descriptorRegistry;
        this.channelPool = channelPool;
    }

    public GrpcInvocationResponse invoke(GrpcInvocationRequest request) {
        Descriptors.MethodDescriptor protoMethod =
                descriptorRegistry.resolveMethod(request.serviceId(), request.fullMethodName());

        Descriptors.Descriptor inputType = protoMethod.getInputType();
        Descriptors.Descriptor outputType = protoMethod.getOutputType();

        MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod = buildGrpcMethodDescriptor(protoMethod, inputType, outputType);

        List<DynamicMessage> requests = buildRequests(inputType, request.payloadsJson());

        ManagedChannel channel = channelPool.getChannel(request.host(), request.port(), request.plaintext());
        CallOptions callOptions = buildCallOptions(request.deadlineMillis());

        MethodDescriptor.MethodType methodType = grpcMethod.getType();
        List<DynamicMessage> responses = switch (methodType) {
            case UNARY -> List.of(ClientCalls.blockingUnaryCall(channel, grpcMethod, callOptions, singleOrThrow(requests)));
            case SERVER_STREAMING -> collectServerStream(channel, grpcMethod, callOptions, singleOrThrow(requests));
            case CLIENT_STREAMING, BIDI_STREAMING -> collectStreamingCall(channel, grpcMethod, callOptions, requests, methodType);
            default -> throw new IllegalStateException("Unsupported method type: " + methodType);
        };

        List<String> responsesJson = new ArrayList<>();
        JsonFormat.Printer printer = JsonFormat.printer().preservingProtoFieldNames();
        for (DynamicMessage m : responses) {
            responsesJson.add(toJsonSafely(printer, m));
        }

        return new GrpcInvocationResponse(methodType.name(), responsesJson);
    }

    private MethodDescriptor<DynamicMessage, DynamicMessage> buildGrpcMethodDescriptor(
            Descriptors.MethodDescriptor protoMethod,
            Descriptors.Descriptor inputType,
            Descriptors.Descriptor outputType) {

        MethodDescriptor.Marshaller<DynamicMessage> requestMarshaller =
                ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(inputType));
        MethodDescriptor.Marshaller<DynamicMessage> responseMarshaller =
                ProtoUtils.marshaller(DynamicMessage.getDefaultInstance(outputType));

        return MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                .setType(resolveMethodType(protoMethod))
                .setFullMethodName(MethodDescriptor.generateFullMethodName(
                        protoMethod.getService().getFullName(), protoMethod.getName()))
                .setRequestMarshaller(requestMarshaller)
                .setResponseMarshaller(responseMarshaller)
                .build();
    }

    private MethodDescriptor.MethodType resolveMethodType(Descriptors.MethodDescriptor m) {
        if (!m.isClientStreaming() && !m.isServerStreaming()) return MethodDescriptor.MethodType.UNARY;
        if (!m.isClientStreaming() && m.isServerStreaming()) return MethodDescriptor.MethodType.SERVER_STREAMING;
        if (m.isClientStreaming() && !m.isServerStreaming()) return MethodDescriptor.MethodType.CLIENT_STREAMING;
        return MethodDescriptor.MethodType.BIDI_STREAMING;
    }

    private List<DynamicMessage> buildRequests(Descriptors.Descriptor inputType, List<String> payloadsJson) {
        if (payloadsJson == null || payloadsJson.isEmpty()) {
            throw new IllegalArgumentException("payloadsJson must contain at least one JSON payload");
        }
        List<DynamicMessage> result = new ArrayList<>();
        for (String json : payloadsJson) {
            DynamicMessage.Builder builder = DynamicMessage.newBuilder(inputType);
            try {
                JsonFormat.parser().ignoringUnknownFields().merge(json, builder);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Failed to parse payload JSON against message type '" + inputType.getFullName() + "': " + e.getMessage(), e);
            }
            result.add(builder.build());
        }
        return result;
    }

    private DynamicMessage singleOrThrow(List<DynamicMessage> requests) {
        if (requests.size() != 1) {
            throw new IllegalArgumentException("This method type expects exactly one request payload, got " + requests.size());
        }
        return requests.get(0);
    }

    private CallOptions buildCallOptions(Long deadlineMillis) {
        CallOptions options = CallOptions.DEFAULT;
        if (deadlineMillis != null) {
            options = options.withDeadline(Deadline.after(deadlineMillis, TimeUnit.MILLISECONDS));
        }
        return options;
    }

    private List<DynamicMessage> collectServerStream(
            ManagedChannel channel,
            MethodDescriptor<DynamicMessage, DynamicMessage> method,
            CallOptions callOptions,
            DynamicMessage request) {
        Iterator<DynamicMessage> iterator = ClientCalls.blockingServerStreamingCall(channel, method, callOptions, request);
        List<DynamicMessage> results = new ArrayList<>();
        iterator.forEachRemaining(results::add);
        return results;
    }

    /** Handles both client-streaming and bidi-streaming by driving a StreamObserver pair and blocking until completion. */
    private List<DynamicMessage> collectStreamingCall(
            ManagedChannel channel,
            MethodDescriptor<DynamicMessage, DynamicMessage> method,
            CallOptions callOptions,
            List<DynamicMessage> requests,
            MethodDescriptor.MethodType methodType) {

        List<DynamicMessage> responses = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        StreamObserver<DynamicMessage> responseObserver = new StreamObserver<>() {
            @Override public void onNext(DynamicMessage value) { responses.add(value); }
            @Override public void onError(Throwable t) { error.set(t); latch.countDown(); }
            @Override public void onCompleted() { latch.countDown(); }
        };

        StreamObserver<DynamicMessage> requestObserver = methodType == MethodDescriptor.MethodType.BIDI_STREAMING
                ? ClientCalls.asyncBidiStreamingCall(channel.newCall(method, callOptions), responseObserver)
                : ClientCalls.asyncClientStreamingCall(channel.newCall(method, callOptions), responseObserver);

        try {
            for (DynamicMessage req : requests) {
                requestObserver.onNext(req);
            }
            requestObserver.onCompleted();

            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new RuntimeException(new TimeoutException("Streaming call did not complete within 30s"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }

        if (error.get() != null) {
            throw new RuntimeException(error.get());
        }
        return responses;
    }

    private String toJsonSafely(JsonFormat.Printer printer, DynamicMessage message) {
        try {
            return printer.print(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize response message to JSON: " + e.getMessage(), e);
        }
    }
}
