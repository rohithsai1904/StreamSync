
package com.example.replicator.utils;

import java.util.List;

import com.example.replicator.utils.DebeziumSchemaParser.SchemaField;

public class CreateTable {

    public static String createTable(
            String tableName,
            List<SchemaField> columns) {

        if (!tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid table name");
        }

        StringBuilder sb = new StringBuilder();

        sb.append("CREATE TABLE IF NOT EXISTS ")
          .append(tableName)
          .append(" (\n");

        for (int i = 0; i < columns.size(); i++) {
            SchemaField column = columns.get(i);

            String columnName = column.fieldName();
            String sqlType = SqlTypeMapper.getSqlType(column.type());

            if (sqlType == null) {
                throw new IllegalArgumentException(
                    "Unsupported SQL type: " + column.type()
                );
            }

            if (!columnName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException(
                    "Invalid column name: " + columnName
                );
            }

            sb.append("    ")
              .append(columnName)
              .append(" ")
              .append(sqlType);

            if (!column.optional()) {
                sb.append(" NOT NULL");
            }

            if (i < columns.size() - 1) {
                sb.append(",");
            }

            sb.append("\n");
        }

        sb.append(");");

        return sb.toString();
    }
}
