package com.example.replicator.utils;


import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

public class DebeziumSchemaParser {

    public record SchemaField(
        String path,
        String type,
        boolean optional,
        String fieldName,
        String defaultValue
    ) {}

    public static List<SchemaField> extractFields(JsonNode schema) {
        List<SchemaField> result = new ArrayList<>();
        extractRecursive(schema, "", result);
        return result;
    }

    private static void extractRecursive(
            JsonNode node,
            String parentPath,
            List<SchemaField> result) {

        JsonNode fields = node.path("fields");

        if (!fields.isArray()) {
            return;
        }

        for (JsonNode field : fields) {
            String name = field.path("field").asText();
            String path = parentPath.isEmpty()
                    ? name
                    : parentPath + "." + name;

            result.add(new SchemaField(
                path,
                field.path("type").asText(),
                field.path("optional").asBoolean(false),
                name,
                field.has("default")
                    ? field.get("default").asText()
                    : null
            ));

            // Recurse into nested structures.
            extractRecursive(field, path, result);
        }
    }
}
