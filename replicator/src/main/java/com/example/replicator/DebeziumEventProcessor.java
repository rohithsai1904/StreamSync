package com.example.replicator;

import com.example.replicator.operation.InsertOperation;
import com.example.replicator.operation.UpdateOperation;
import com.example.replicator.operation.DeleteOperation;
import com.example.replicator.utils.DebeziumSchemaParser;
import com.example.replicator.utils.DebeziumSchemaParser.SchemaField;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DebeziumEventProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final InsertOperation insertOperation;
    private final UpdateOperation updateOperation;
    private final DeleteOperation deleteOperation;

    public DebeziumEventProcessor(
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

        List<SchemaField> fields =
            DebeziumSchemaParser.extractFields(schema);

        switch (operation) {
            case "c":
            case "r":
                insertOperation.perform_operation(
                    before, after, source, fields);
                break;

            case "u":
                updateOperation.perform_operation(
                    before, after, source, fields);
                break;

            case "d":
                deleteOperation.perform_operation(
                    before, after, source, fields);
                break;

            default:
                throw new IllegalArgumentException(
                    "Unsupported Debezium operation: " + operation
                );
        }
    }
}