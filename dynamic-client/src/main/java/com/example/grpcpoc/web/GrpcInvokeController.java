package com.example.grpcpoc.web;

import com.example.grpcpoc.invoke.DynamicGrpcInvoker;
import com.example.grpcpoc.invoke.GrpcInvocationRequest;
import com.example.grpcpoc.invoke.GrpcInvocationResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/grpc")
public class GrpcInvokeController {

    private final DynamicGrpcInvoker invoker;

    public GrpcInvokeController(DynamicGrpcInvoker invoker) {
        this.invoker = invoker;
    }

    @PostMapping("/invoke")
    public ResponseEntity<GrpcInvocationResponse> invoke(@RequestBody GrpcInvocationRequest request) {
        return ResponseEntity.ok(invoker.invoke(request));
    }
}
