package com.example.replicator.operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import com.example.replicator.target.DebeziumTableService;
import com.example.replicator.utils.DebeziumSchemaParser.SchemaField;

@Component
public class UpdateOperation implements DataBaseOperation{
    
    private final JdbcTemplate jdbcTemplate;
    private final DebeziumTableService debeziumTableService;
    private ObjectMapper objectMapper = new ObjectMapper();

    public UpdateOperation( @Qualifier("targetJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        debeziumTableService = new DebeziumTableService(this.jdbcTemplate);
    }
    
    @Override 
    public void perform_operation(JsonNode before,JsonNode after,JsonNode source,List<SchemaField> columnFields){

        if (after == null || after.isNull()) {
            throw new IllegalArgumentException("After record is missing for INSERT");
        }

        if (before == null || before.isNull()) {
            throw new IllegalArgumentException("After record is missing for INSERT");
        }

        String tableName = source.path("table").asText();

        debeziumTableService.alterTable(tableName, columnFields);

        Map<String, Object> beforeRecords = objectMapper.convertValue(
            before,
            new com.fasterxml.jackson.core.type.TypeReference<
                Map<String, Object>>() {}
        );

        Map<String, Object> afterRecords = objectMapper.convertValue(
            after,
            new com.fasterxml.jackson.core.type.TypeReference<
                Map<String, Object>>() {}
        );

        debeziumTableService.updateRecords(tableName, beforeRecords, afterRecords);
    }
}
