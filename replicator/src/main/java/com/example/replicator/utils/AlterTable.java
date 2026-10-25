package com.example.replicator.utils;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

import com.example.replicator.utils.DebeziumSchemaParser.SchemaField;

public class AlterTable {

    public static String alterTable(String tableName,List<SchemaField> sourceColumnFields,List<String> targetColumns){
        
        Set<String> targetColumnSet = new HashSet<>(targetColumns);
        
        List<SchemaField> extraColumns = sourceColumnFields.stream()
            .filter(col -> !targetColumnSet.contains(col.fieldName()))
            .toList();

        if(extraColumns.isEmpty() && sourceColumnFields.size()-targetColumns.size()==0){
            return "";
        }
        
        if(extraColumns.size()==sourceColumnFields.size()-targetColumns.size()){
            return addColumns(tableName, extraColumns);
        }

        return "";
        
    }

    public static String addColumns(String tableName,List<SchemaField> columns){

        StringBuilder sb = new StringBuilder();

        sb.append("ALTER TABLE ").append(tableName).append(" ");

        for (int i = 0; i < columns.size(); i++) {
            SchemaField column = columns.get(i);

            String sqlType = SqlTypeMapper.getSqlType(column.type());

            if (sqlType == null) {
                throw new IllegalArgumentException(
                    "Unsupported type: " + column.type()
                );
            }

            if (i > 0) {
                sb.append(", ");
            }

            sb.append("ADD COLUMN IF NOT EXISTS \"")
            .append(column.fieldName())
            .append("\" ")
            .append(sqlType);
        }

        return sb.append(";").toString();
    }
    
   
}

