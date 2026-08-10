package com.example.grpcpoc.descriptor;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryDescriptorStore implements DescriptorStore {

    private final Map<String, byte[]> store = new ConcurrentHashMap<>();

    @Override
    public Optional<byte[]> getDescriptorSet(String serviceId) {
        return Optional.ofNullable(store.get(serviceId));
    }

    @Override
    public void putDescriptorSet(String serviceId, byte[] descriptorSetBytes) {
        store.put(serviceId, descriptorSetBytes);
    }

    @Override
    public void putDescriptorSetFromFile(String serviceId, Path descriptorFilePath) throws IOException {
        store.put(serviceId, Files.readAllBytes(descriptorFilePath));
    }

    @Override
    public void remove(String serviceId) {
        store.remove(serviceId);
    }

    @Override
    public Set<String> listServiceIds() {
        return store.keySet();
    }
}
