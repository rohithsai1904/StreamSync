package com.example.replicator.operation;

import java.util.List;
import com.example.replicator.utils.DebeziumSchemaParser.SchemaField;

import com.fasterxml.jackson.databind.JsonNode;

public interface  DataBaseOperation {

    public void perform_operation(JsonNode before,JsonNode after,JsonNode source,List<SchemaField> fields);
}
