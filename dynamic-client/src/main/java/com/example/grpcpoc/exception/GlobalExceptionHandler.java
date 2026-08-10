package com.example.grpcpoc.exception;

import com.example.grpcpoc.descriptor.DescriptorRegistry;
import io.grpc.StatusRuntimeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StatusRuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleGrpcStatus(StatusRuntimeException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "GRPC_CALL_FAILED");
        body.put("grpcStatusCode", e.getStatus().getCode().name());
        body.put("message", e.getStatus().getDescription());
        // Map gRPC's own status codes onto sensible-ish HTTP codes for the REST caller.
        HttpStatus http = switch (e.getStatus().getCode()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_ARGUMENT, FAILED_PRECONDITION, OUT_OF_RANGE -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(http).body(body);
    }

    @ExceptionHandler(DescriptorRegistry.DescriptorResolutionException.class)
    public ResponseEntity<Map<String, Object>> handleDescriptorError(DescriptorRegistry.DescriptorResolutionException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "DESCRIPTOR_RESOLUTION_FAILED",
                "message", e.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "BAD_REQUEST",
                "message", e.getMessage()
        ));
    }
}
