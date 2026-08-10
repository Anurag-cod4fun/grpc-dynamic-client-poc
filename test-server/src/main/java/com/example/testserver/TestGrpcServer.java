package com.example.testserver;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;

public class TestGrpcServer {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6565;

        Server server = NettyServerBuilder.forPort(port)
                .addService(new GreeterServiceImpl())
                .build()
                .start();

        System.out.println("Test gRPC server listening on port " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
        server.awaitTermination();
    }
}
