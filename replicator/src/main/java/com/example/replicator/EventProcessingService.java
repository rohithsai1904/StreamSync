package com.example.replicator;

import com.example.replicator.target.IdempotentUpsert;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventProcessingService {

    private final IdempotentUpsert idempotentUpsert;
    private final DebeziumEventProcessor eventProcessor;

    public EventProcessingService(
            IdempotentUpsert idempotentUpsert,
            DebeziumEventProcessor eventProcessor) {
        this.idempotentUpsert = idempotentUpsert;
        this.eventProcessor = eventProcessor;
    }

    @Transactional(transactionManager = "targetTransactionManager")
    public boolean process(ConsumerRecord<String, String> record)
            throws Exception {

        if (!idempotentUpsert.canProcessEvent(record)) {
            System.out.println("Already in DataBase");
            return false;
        }

        eventProcessor.parse(record.value());

        return true;
    }
}