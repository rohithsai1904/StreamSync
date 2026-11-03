package com.example.replicator;

import java.util.*;

public record ComparisionResult(String tableName, List<Long> missingInTarget, List<Long> extraInTarget, List<Long> mismatched) {
}
