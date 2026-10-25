package com.example.replicator.operation;

import com.example.replicator.target.DebeziumTableService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import com.example.replicator.utils.DebeziumSchemaParser.SchemaField;

@Component
public class InsertOperation implements DataBaseOperation{

    private final JdbcTemplate jdbcTemplate;
    private final DebeziumTableService debeziumTableService;
    private ObjectMapper objectMapper = new ObjectMapper();

    public InsertOperation( @Qualifier("targetJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        debeziumTableService = new DebeziumTableService(this.jdbcTemplate);
    }
    
    @Override 
    public void perform_operation(JsonNode before,JsonNode after,JsonNode source,List<SchemaField> fields){

        if (after == null || after.isNull()) {
            throw new IllegalArgumentException("After record is missing for INSERT");
        }
        if(before!=null)
            throw new IllegalArgumentException("Before record should be empty for INSERT");
        
        String tableName = source.path("table").asText();

        List<SchemaField> columnFields = fields.stream()
                .filter(f -> f.path().startsWith("after.")).toList();

        debeziumTableService.createTableIfNotExists(tableName,columnFields);

        debeziumTableService.alterTable(tableName,columnFields);
        
        Map<String, Object> insertRecords =objectMapper.convertValue(
                after,
                new com.fasterxml.jackson.core.type.TypeReference<
                    Map<String, Object>>() {}
            );
        debeziumTableService.insertRowIntoTable(tableName, insertRecords);
    }
}
