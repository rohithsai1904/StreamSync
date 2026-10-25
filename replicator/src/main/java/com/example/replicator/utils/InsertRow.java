package com.example.replicator.utils;

import java.util.stream.Collectors;
import java.util.Map;

public class InsertRow {

    public static String insertRow(String tableName,Map<String, ?> insertRecords){

        StringBuilder sb = new StringBuilder();

        sb.append("INSERT INTO "+tableName + "( \n");

        String columns = insertRecords.keySet().stream()
            .map(column -> "\"" + column + "\"")
            .collect(Collectors.joining(", "));
        
        sb.append(columns+") VALUES (" );

        String parameters = insertRecords.keySet().stream()
            .map(column -> "?")
            .collect(Collectors.joining(", "));
        
        sb.append(parameters+");" );
        return sb.toString();
    }
}
