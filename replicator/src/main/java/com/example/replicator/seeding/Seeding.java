package com.example.replicator.seeding;

import org.springframework.stereotype.Service;
import com.example.replicator.target.DebeziumTableService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.*;
import com.example.replicator.ComparisionResult;

@Service
public class Seeding {

    private final JdbcTemplate sourceJdbcTemplate;
    private final JdbcTemplate targetJdbcTemplate;

    private final DebeziumTableService sourceDebeziumTableService;
    private final DebeziumTableService targetDebeziumTableService;

    public Seeding(@Qualifier("sourceJdbcTemplate") JdbcTemplate sourceJdbcTemplate, @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate){
        this.sourceJdbcTemplate = sourceJdbcTemplate;
        this.targetJdbcTemplate = targetJdbcTemplate;

        this.sourceDebeziumTableService = new DebeziumTableService(this.sourceJdbcTemplate);
        this.targetDebeziumTableService = new DebeziumTableService(this.targetJdbcTemplate);
    }

    public void seed(ComparisionResult result) {
        String tableName = result.tableName();
        List<Long> missingInTarget = result.missingInTarget();
        List<Long> extraInTarget = result.extraInTarget();
        List<Long> mismatched = result.mismatched();

        //Insert in Target
        for(long id: missingInTarget){
            Map<String, Object> row = sourceDebeziumTableService.getRowByID(tableName, id);
            targetDebeziumTableService.insertRowIntoTable(tableName, row);
        }
        //Update in Target
        for(long id: mismatched){
            Map<String, Object> after = sourceDebeziumTableService.getRowByID(tableName, id);

            targetDebeziumTableService.updateRecords(tableName, after);
        }

        //Insert in Source
        for(long id: extraInTarget){
            Map<String, Object> row = targetDebeziumTableService.getRowByID(tableName, id);
            sourceDebeziumTableService.insertRowIntoTable(tableName, row);
        }
    }
}