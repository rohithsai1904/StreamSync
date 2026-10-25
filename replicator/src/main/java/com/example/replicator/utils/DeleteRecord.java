package com.example.replicator.utils;

import java.util.Map;

public class DeleteRecord {
    public static String deletedRecord(String tableName, Map<String, ?> before ){
        String sql = "DELETE FROM "+ tableName + " WHERE id = ?";
        return sql;
    }
}
