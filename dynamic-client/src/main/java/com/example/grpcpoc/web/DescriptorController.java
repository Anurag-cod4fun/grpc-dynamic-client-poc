package com.example.grpcpoc.web;

import com.example.grpcpoc.descriptor.DescriptorRegistry;
import com.example.grpcpoc.descriptor.DescriptorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/descriptors")
public class DescriptorController {

    private final DescriptorStore descriptorStore;
    private final DescriptorRegistry descriptorRegistry;

    public DescriptorController(DescriptorStore descriptorStore, DescriptorRegistry descriptorRegistry) {
        this.descriptorStore = descriptorStore;
        this.descriptorRegistry = descriptorRegistry;
    }

    @GetMapping("/{serviceId}/methods")
    public ResponseEntity<List<String>> listMethods(@PathVariable String serviceId) {
        return ResponseEntity.ok(descriptorRegistry.listMethods(serviceId));
    }

    @GetMapping
    public ResponseEntity<Set<String>> listServices() {
        return ResponseEntity.ok(descriptorStore.listServiceIds());
    }
}
