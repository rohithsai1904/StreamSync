package com.example.replicator;

import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

@Service
public class KafkaListenerService {

    private final KafkaListenerEndpointRegistry registry;

    public KafkaListenerService(KafkaListenerEndpointRegistry registry) {
        this.registry = registry;
    }

    public void start() {
        registry.getListenerContainers()
                .forEach(container -> {
                    if (!container.isRunning()) {
                        container.start();
                    }
                });

        System.out.println("Kafka listener started.");
    }

    public void stop() {
        registry.getListenerContainers()
                .forEach(container -> {
                    if (container.isRunning()) {
                        container.stop();
                    }
                });

        System.out.println("Kafka listener stopped.");
    }
}