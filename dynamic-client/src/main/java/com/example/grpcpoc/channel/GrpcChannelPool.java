package com.example.grpcpoc.channel;

import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Caches one {@link ManagedChannel} per (host, port, plaintext) target so we don't pay
 * connection setup cost on every invocation. gRPC channels are safe to share across
 * threads/virtual threads and are meant to be long-lived.
 *
 * POC uses plaintext by default. For real external backends, add a TLS-enabled path
 * (NettyChannelBuilder#sslContext) keyed by target + trust config, the same way you'd
 * key mTLS configs per Feign client target.
 */
@Component
public class GrpcChannelPool {

    private static final Logger log = LoggerFactory.getLogger(GrpcChannelPool.class);

    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public ManagedChannel getChannel(String host, int port, boolean plaintext) {
        String key = host + ":" + port + ":" + plaintext;
        return channels.computeIfAbsent(key, k -> {
            log.info("Opening new gRPC channel to {}:{} (plaintext={})", host, port, plaintext);
            NettyChannelBuilder builder = NettyChannelBuilder.forAddress(host, port);
            if (plaintext) {
                builder.usePlaintext();
            }
            // Sensible POC defaults; tune per backend SLAs in the real framework.
            builder.keepAliveTime(30, TimeUnit.SECONDS)
                   .keepAliveTimeout(10, TimeUnit.SECONDS);
            return builder.build();
        });
    }

    @PreDestroy
    public void shutdownAll() {
        channels.values().forEach(ch -> {
            try {
                ch.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }
}
