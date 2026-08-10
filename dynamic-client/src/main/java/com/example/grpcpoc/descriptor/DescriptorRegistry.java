package com.example.grpcpoc.descriptor;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorSet;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.Descriptors.ServiceDescriptor;
import com.google.protobuf.InvalidProtocolBufferException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns raw {@code FileDescriptorSet} bytes (produced by {@code protoc --descriptor_set_out
 * --include_imports}) into live protobuf {@link FileDescriptor} objects, and resolves
 * "package.Service/Method" strings to {@link MethodDescriptor}s.
 *
 * This is the piece that replaces generated *Grpc.java / *OuterClass.java stub classes:
 * everything they would have hard-coded at compile time, we resolve here at runtime.
 */
@Component
public class DescriptorRegistry {

    private final DescriptorStore descriptorStore;

    /** serviceId -> (fully-qualified .proto file name -> resolved FileDescriptor) */
    private final Map<String, Map<String, FileDescriptor>> resolvedCache = new ConcurrentHashMap<>();

    public DescriptorRegistry(DescriptorStore descriptorStore) {
        this.descriptorStore = descriptorStore;
    }

    /** Call after re-uploading a descriptor set for a serviceId so stale FileDescriptors aren't reused. */
    public void invalidate(String serviceId) {
        resolvedCache.remove(serviceId);
    }

    /**
     * Resolves a fully qualified gRPC method reference, e.g.
     * {@code "helloworld.Greeter/SayHello"}, against the descriptor set registered
     * for the given serviceId.
     */
    public MethodDescriptor resolveMethod(String serviceId, String fullMethodName) {
        int slash = fullMethodName.lastIndexOf('/');
        if (slash < 0) {
            throw new DescriptorResolutionException(
                    "fullMethodName must be of the form 'package.Service/Method', got: " + fullMethodName);
        }
        String serviceFullName = fullMethodName.substring(0, slash);
        String methodName = fullMethodName.substring(slash + 1);

        ServiceDescriptor serviceDescriptor = resolveService(serviceId, serviceFullName);
        MethodDescriptor method = serviceDescriptor.findMethodByName(methodName);
        if (method == null) {
            throw new DescriptorResolutionException(
                    "No method '" + methodName + "' on service '" + serviceFullName + "' for serviceId '" + serviceId + "'");
        }
        return method;
    }

    public ServiceDescriptor resolveService(String serviceId, String serviceFullName) {
        Map<String, FileDescriptor> files = resolveAllFiles(serviceId);
        for (FileDescriptor fd : files.values()) {
            ServiceDescriptor sd = fd.findServiceByName(simpleServiceName(fd, serviceFullName));
            if (sd != null && sd.getFullName().equals(serviceFullName)) {
                return sd;
            }
        }
        throw new DescriptorResolutionException(
                "No service '" + serviceFullName + "' found in descriptor set for serviceId '" + serviceId + "'");
    }

    /** Lists "package.Service/Method" for every method across every service in a serviceId's descriptor set. Handy for a debug/listing endpoint. */
    public List<String> listMethods(String serviceId) {
        Map<String, FileDescriptor> files = resolveAllFiles(serviceId);
        return files.values().stream()
                .flatMap(fd -> fd.getServices().stream())
                .flatMap(sd -> sd.getMethods().stream()
                        .map(m -> sd.getFullName() + "/" + m.getName()))
                .toList();
    }

    private String simpleServiceName(FileDescriptor fd, String serviceFullName) {
        // strip the file's package prefix to get the simple service name findServiceByName expects
        String pkg = fd.getPackage();
        if (!pkg.isEmpty() && serviceFullName.startsWith(pkg + ".")) {
            return serviceFullName.substring(pkg.length() + 1);
        }
        return serviceFullName;
    }

    private Map<String, FileDescriptor> resolveAllFiles(String serviceId) {
        return resolvedCache.computeIfAbsent(serviceId, this::buildFileDescriptors);
    }

    private Map<String, FileDescriptor> buildFileDescriptors(String serviceId) {
        byte[] bytes = descriptorStore.getDescriptorSet(serviceId)
                .orElseThrow(() -> new DescriptorResolutionException(
                        "No descriptor set registered for serviceId '" + serviceId + "'. Upload one via POST /descriptors/" + serviceId));

        FileDescriptorSet fileDescriptorSet;
        try {
            fileDescriptorSet = FileDescriptorSet.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new DescriptorResolutionException(
                    "Descriptor set bytes for serviceId '" + serviceId + "' are not a valid FileDescriptorSet. " +
                    "Make sure it was produced with: protoc --descriptor_set_out=out.desc --include_imports your.proto", e);
        }

        Map<String, FileDescriptorProto> protoByName = new HashMap<>();
        for (FileDescriptorProto proto : fileDescriptorSet.getFileList()) {
            protoByName.put(proto.getName(), proto);
        }

        Map<String, FileDescriptor> resolved = new HashMap<>();
        for (String fileName : protoByName.keySet()) {
            resolveFile(fileName, protoByName, resolved, new java.util.ArrayDeque<>());
        }
        return resolved;
    }

    /** Recursively builds FileDescriptors, resolving `import "..."` dependencies first. Detects cycles. */
    private FileDescriptor resolveFile(String fileName,
                                        Map<String, FileDescriptorProto> protoByName,
                                        Map<String, FileDescriptor> resolved,
                                        java.util.Deque<String> inProgress) {
        FileDescriptor cached = resolved.get(fileName);
        if (cached != null) {
            return cached;
        }
        if (inProgress.contains(fileName)) {
            throw new DescriptorResolutionException("Circular import detected involving: " + fileName);
        }
        FileDescriptorProto proto = protoByName.get(fileName);
        if (proto == null) {
            throw new DescriptorResolutionException(
                    "Missing dependency '" + fileName + "' in descriptor set. Re-generate with --include_imports.");
        }

        inProgress.push(fileName);
        FileDescriptor[] deps = proto.getDependencyList().stream()
                .map(dep -> resolveFile(dep, protoByName, resolved, inProgress))
                .toArray(FileDescriptor[]::new);
        inProgress.pop();

        try {
            FileDescriptor fd = FileDescriptor.buildFrom(proto, deps);
            resolved.put(fileName, fd);
            return fd;
        } catch (DescriptorValidationException e) {
            throw new DescriptorResolutionException("Failed to build FileDescriptor for '" + fileName + "': " + e.getMessage(), e);
        }
    }

    public static class DescriptorResolutionException extends RuntimeException {
        public DescriptorResolutionException(String message) {
            super(message);
        }
        public DescriptorResolutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
