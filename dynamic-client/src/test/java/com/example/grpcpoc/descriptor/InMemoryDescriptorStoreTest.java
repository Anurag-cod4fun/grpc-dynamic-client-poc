package com.example.grpcpoc.descriptor;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryDescriptorStoreTest {

    @Test
    void putDescriptorSetFromFileLoadsBytesFromLocalPath() throws IOException {
        Path tempFile = Files.createTempFile("descriptor", ".desc");
        Files.write(tempFile, new byte[]{1, 2, 3, 4, 5});

        InMemoryDescriptorStore store = new InMemoryDescriptorStore();
        store.putDescriptorSetFromFile("greeter", tempFile);

        assertTrue(store.getDescriptorSet("greeter").isPresent());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, store.getDescriptorSet("greeter").orElseThrow());
    }
}
