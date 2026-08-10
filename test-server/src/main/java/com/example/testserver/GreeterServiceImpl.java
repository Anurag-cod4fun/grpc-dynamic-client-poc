package com.example.testserver;

import com.example.testserver.proto.GreeterGrpc;
import com.example.testserver.proto.HelloReply;
import com.example.testserver.proto.HelloRequest;
import io.grpc.stub.StreamObserver;

public class GreeterServiceImpl extends GreeterGrpc.GreeterImplBase {

    @Override
    public void sayHello(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        HelloReply reply = HelloReply.newBuilder()
                .setMessage("Hello, " + request.getName() + "! (from real generated stubs)")
                .build();
        responseObserver.onNext(reply);
        responseObserver.onCompleted();
    }

    @Override
    public void sayHelloStream(HelloRequest request, StreamObserver<HelloReply> responseObserver) {
        for (int i = 1; i <= 3; i++) {
            responseObserver.onNext(HelloReply.newBuilder()
                    .setMessage("Hello, " + request.getName() + "! (message " + i + " of 3)")
                    .build());
        }
        responseObserver.onCompleted();
    }
}
