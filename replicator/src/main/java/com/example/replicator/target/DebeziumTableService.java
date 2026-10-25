package com.example.replicator.target;


import org.springframework.jdbc.core.JdbcTemplate;

import com.example.replicator.utils.AlterTable;
import com.example.replicator.utils.CreateTable;
import com.example.replicator.utils.InsertRow;
import com.example.replicator.utils.UpdateRecords;
import com.example.replicator.utils.DeleteRecord;
import com.example.replicator.utils.DebeziumSchemaParser.SchemaField;

import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;

public class DebeziumTableService {
    private final JdbcTemplate jdbcTemplate;

    public DebeziumTableService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createTableIfNotExists(String tableName ,List<SchemaField> schemaFields) {
        String sql = CreateTable.createTable(
            tableName,
            schemaFields
        );

        System.out.println("Creating TABLE IF NOT EXISTS \n" +sql);
        jdbcTemplate.execute(sql);
    }

    public void insertRowIntoTable(String table,Map<String, ?> insertRecords){
        String sql = InsertRow.insertRow(table, insertRecords);
        Object[] values = insertRecords.values().toArray();
        
        System.out.println("SQL: " + sql);
        System.out.println("Values: " + Arrays.toString(values));

        int rowsAffected = jdbcTemplate.update(sql, values);
        System.out.println("Rows inserted: " + rowsAffected);
    }

    private List<String> getDestinationColumns(String tableName) {

        String sql = """
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND table_name = ?
            ORDER BY ordinal_position
            """;
    
        return jdbcTemplate.queryForList(
            sql,
            String.class,
            tableName
        );
    }


    public void alterTable(String tableName,List<SchemaField> columnFields){
        List<String> destinationColumns = getDestinationColumns(tableName);

        String sql = AlterTable.alterTable(tableName, columnFields, destinationColumns);

        if(sql!=null){
            jdbcTemplate.execute(sql);
        }
    }

    public void updateRecords(String tableName,Map<String, ?> before,Map<String, ?> after){

        String sql = UpdateRecords.updateRecords(tableName, before, after);
        
        List<Object> values = new ArrayList<>(UpdateRecords.getUpdatedValues(before, after).values());

        values.add(before.get("id"));

        jdbcTemplate.update(sql,values.toArray());
    }

    public void deleteRecord(String tableName,Map<String, ?> before){

        String sql = DeleteRecord.deletedRecord(tableName, before);
        
        List<Object> values = new ArrayList<>();

        values.add(before.get("id"));

        jdbcTemplate.update(sql,values.toArray());
    }
}
