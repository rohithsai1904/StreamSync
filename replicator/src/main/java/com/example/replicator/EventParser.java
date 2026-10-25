package com.example.replicator;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.example.replicator.operation.InsertOperation;
import com.example.replicator.operation.UpdateOperation;
import com.example.replicator.utils.DebeziumSchemaParser;
import com.example.replicator.utils.DebeziumSchemaParser.SchemaField;
import com.example.replicator.operation.DeleteOperation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@Component
public class EventParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final InsertOperation insertOperation;
    private final UpdateOperation updateOperation;
    private final DeleteOperation deleteOperation;

    public EventParser(
            InsertOperation insertOperation,
            UpdateOperation updateOperation,
            DeleteOperation deleteOperation) {
        this.insertOperation = insertOperation;
        this.updateOperation = updateOperation;
        this.deleteOperation = deleteOperation;
    }

    public void parse(String message) throws Exception {

        JsonNode root = objectMapper.readTree(message);
        JsonNode payload = root.path("payload");
        JsonNode schema = root.path("schema");

        if (payload.isMissingNode() || payload.isNull()) {
            throw new IllegalArgumentException(
                "Debezium event does not contain a payload"
            );
        }

        String operation = payload.path("op").asText();

        JsonNode before = payload.path("before");
        JsonNode after = payload.path("after");
        JsonNode source = payload.path("source");
        
        List<SchemaField> fields = DebeziumSchemaParser.extractFields(schema);

        switch (operation) {
            case "c":
            case "r":
                insertOperation.perform_operation(before, after, source,fields);
                break;

            case "u":
                updateOperation.perform_operation(before, after, source,fields);
                break;

            case "d":
                deleteOperation.perform_operation(before, after, source,fields);
                break;

            default:
                throw new IllegalArgumentException(
                    "Unsupported Debezium operation: " + operation
                );
        }
    }

    @KafkaListener(
        topics = "${app.kafka.topic}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(String event) {

        try {
            System.out.println("Received Kafka event: " + event);
            parse(event);
        } catch (Exception e) {
            System.err.println("Failed to process Kafka event: " + e.getMessage());
            throw new RuntimeException("CDC event processing failed", e);
        }
    }
}