package com.example.grpcpoc.descriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Abstraction over wherever raw {@code FileDescriptorSet} bytes live for a given
 * external backend's proto contract.
 *
 * In this POC it's backed by an in-memory map populated via the REST upload endpoint.
 * In the real framework, swap this for a VaultDescriptorStore that reads the bytes
 * from a Vault KV path keyed by serviceId (+ version), matching the storage model
 * you're already using for the fixed-contract gRPC server side.
 */
public interface DescriptorStore {

    /**
     * @param serviceId logical identifier for the external backend, e.g. "payments-backend-v2"
     * @return raw bytes of a compiled {@code FileDescriptorSet} (protoc --descriptor_set_out
     *         --include_imports output), if registered
     */
    Optional<byte[]> getDescriptorSet(String serviceId);

    void putDescriptorSet(String serviceId, byte[] descriptorSetBytes);

    void putDescriptorSetFromFile(String serviceId, Path descriptorFilePath) throws IOException;

    void remove(String serviceId);

    java.util.Set<String> listServiceIds();
}
