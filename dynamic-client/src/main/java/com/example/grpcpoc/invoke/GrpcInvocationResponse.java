package com.example.grpcpoc.invoke;

import java.util.List;

/**
 * @param methodType     UNARY / SERVER_STREAMING / CLIENT_STREAMING / BIDI_STREAMING
 * @param responsesJson  one JSON object per response message received
 */
public record GrpcInvocationResponse(
        String methodType,
        List<String> responsesJson
) {
}
