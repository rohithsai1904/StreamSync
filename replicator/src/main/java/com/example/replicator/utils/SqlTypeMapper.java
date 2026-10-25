package com.example.replicator.utils;

import java.util.Map;

public final class SqlTypeMapper {

    private SqlTypeMapper() {
        // Utility class
    }

    public static final Map<String, String> SQL_TYPES = Map.of(
        "int8", "SMALLINT",
        "int16", "SMALLINT",
        "int32", "INTEGER",
        "int64", "BIGINT",
        "string", "VARCHAR(255)",
        "boolean", "BOOLEAN",
        "float", "REAL",
        "double", "DOUBLE PRECISION",
        "bytes", "BYTEA"
    );

    public static String getSqlType(String debeziumType) {
        String sqlType = SQL_TYPES.get(debeziumType);

        if (sqlType == null) {
            throw new IllegalArgumentException(
                "Unsupported Debezium type: " + debeziumType
            );
        }

        return sqlType;
    }
}