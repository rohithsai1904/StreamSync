package com.example.replicator;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.apache.kafka.clients.consumer.ConsumerRecord;


@Component
public class EventParser {


    private final EventProcessingService eventProcessingService;

    public EventParser(EventProcessingService eventProcessingService) {
        this.eventProcessingService = eventProcessingService;
    }

    @KafkaListener(
        topics = "${app.kafka.topic}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record,Acknowledgment ack) {
        try {
            if (record.value() == null) {
                System.out.println(
                    "Received tombstone for key: " + record.key()
                );
                ack.acknowledge();
                return;
            }
    
            System.out.println("Received Kafka event: " + record.value());
    
            eventProcessingService.process(record);
    
            ack.acknowledge();
    
        } catch (Exception e) {
            System.err.println(
                "Failed to process Kafka event: " + e.getMessage()
            );
    
            throw new RuntimeException("CDC event processing failed", e);
        }
    }
}