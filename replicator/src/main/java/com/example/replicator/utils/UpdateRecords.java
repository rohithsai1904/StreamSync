package com.example.replicator.utils;

import java.util.Map;
import java.util.HashMap;
import java.util.Objects;

public class UpdateRecords {
    
    public static String updateRecords( String tableName, Map<String, ?> before, Map<String, ?> after) {
        Map<String, Object> updatedMap = getUpdatedValues(before, after);

        if (updatedMap.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("UPDATE ").append(tableName).append(" SET ");

        int c = 0;

        for (String column : updatedMap.keySet()) {
            if (c > 0) {
                sb.append(", ");
            }

            sb.append(column).append(" = ?");
            c++;
        }

        sb.append(" WHERE id = ?");

        return sb.toString();
    }

    public static Map<String,Object> getUpdatedValues(Map<String, ?> before,Map<String, ?> after){
        
        Map<String,Object> updatedMap = new HashMap<>();
        
        for(Map.Entry<String,?> entry : after.entrySet()){

            String key = entry.getKey();

            Object beforeValue = before.get(key);
            Object afterValue = entry.getValue();

            if(!Objects.equals(beforeValue,afterValue)){
                updatedMap.put(key,afterValue);
            }
        }

        return updatedMap;
    }
}
