package com.example.grpcpoc.invoke;

import java.util.List;

/**
 * @param serviceId       key under which the target's descriptor set was registered
 * @param host            target backend host
 * @param port            target backend port
 * @param plaintext       true for plaintext (POC/dev), false to (eventually) use TLS
 * @param fullMethodName  "package.Service/Method", e.g. "helloworld.Greeter/SayHello"
 * @param payloadsJson    one JSON object per request message to send. Unary and
 *                        server-streaming calls use exactly one element; client-streaming
 *                        and bidi calls may send several.
 * @param deadlineMillis  optional per-call deadline
 */
public record GrpcInvocationRequest(
        String serviceId,
        String host,
        int port,
        boolean plaintext,
        String fullMethodName,
        List<String> payloadsJson,
        Long deadlineMillis
) {
}
