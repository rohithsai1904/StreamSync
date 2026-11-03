package com.example.replicator.utils;

import java.util.Map;

public class UpdateRecords {
    
    public static String updateRecords( String tableName, Map<String, ?> after) {

        if (after.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(tableName).append(" SET ");

        int c = 0;

        for (String column : after.keySet()) {
            
            if ("id".equalsIgnoreCase(column)) {
                continue;
            }

            if (c > 0) {
                sb.append(", ");
            }

            sb.append(column).append(" = ?");
            c++;
        }

        sb.append(" WHERE id = ?");

        return sb.toString();
    }
}
