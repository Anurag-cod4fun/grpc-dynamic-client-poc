package com.example.grpcpoc.descriptor;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LocalDescriptorLoader {

    private static final Logger log = LoggerFactory.getLogger(LocalDescriptorLoader.class);

    private final DescriptorStore descriptorStore;
    private final DescriptorRegistry descriptorRegistry;
    private final String descriptorPath;
    private final String serviceId;

    public LocalDescriptorLoader(
            DescriptorStore descriptorStore,
            DescriptorRegistry descriptorRegistry,
            @Value("${grpc.descriptor.path:}") String descriptorPath,
            @Value("${grpc.descriptor.service-id:}") String serviceId) {
        this.descriptorStore = descriptorStore;
        this.descriptorRegistry = descriptorRegistry;
        this.descriptorPath = descriptorPath;
        this.serviceId = serviceId;
    }

    @PostConstruct
    public void loadDescriptorFromLocalFile() throws IOException {
        if (!StringUtils.hasText(serviceId) || !StringUtils.hasText(descriptorPath)) {
            log.info("No local descriptor path configured; skipping descriptor preloading.");
            return;
        }

        Path path = Paths.get(descriptorPath);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Configured descriptor file does not exist: " + path.toAbsolutePath());
        }

        descriptorStore.putDescriptorSetFromFile(serviceId, path);
        descriptorRegistry.invalidate(serviceId);
        log.info("Loaded descriptor from local file {} for serviceId {}", path.toAbsolutePath(), serviceId);
    }
}
