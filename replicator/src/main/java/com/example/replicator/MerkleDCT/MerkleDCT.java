package com.example.replicator.MerkleDCT;

import java.util.*;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.example.replicator.ComparisionResult;

@Component
public class MerkleDCT {

    static final int FANOUT = 16;
    static final long LEAF_WIDTH = 2000;


    record Hash(long count, long xor) {
        static final Hash EMPTY = new Hash(0, 0);
    }

    //public record ComparisionResult(String tableName, List<Long> missingInTarget, List<Long> extraInTarget, List<Long> mismatched) {
    //}

    private final JdbcTemplate sourceJdbcTemplate;
    private final JdbcTemplate targetJdbcTemplate;

    public MerkleDCT(@Qualifier("sourceJdbcTemplate") JdbcTemplate sourceJdbcTemplate,
                     @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate) {
        this.sourceJdbcTemplate = sourceJdbcTemplate;
        this.targetJdbcTemplate = targetJdbcTemplate;
    }

    public ComparisionResult compareData(String tableName) {

        Long[] sourceBounds = getKeyBounds(sourceJdbcTemplate, tableName);
        Long[] targetBounds = getKeyBounds(targetJdbcTemplate, tableName);

        ComparisionResult result = new ComparisionResult(tableName, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

        Long sourceMin = sourceBounds[0];
        Long sourceMax = sourceBounds[1];
        Long targetMin = targetBounds[0];
        Long targetMax = targetBounds[1];

        if (sourceMin == null && targetMin == null) {
            return result;
        }

        long min;
        long max;
        if (sourceMin == null) {
            min = targetMin;
            max = targetMax;
        } else if (targetMin == null) {
            min = sourceMin;
            max = sourceMax;
        } else {
            min = Math.min(sourceMin, targetMin);
            max = Math.max(sourceMax, targetMax);
        }

        descend(min, max + 1, result);

        return result;
    }

    private void descend(long low, long high, ComparisionResult result) {

        long width = high - low;

        if (width <= LEAF_WIDTH) {
            compareIndividual(low, high, result);
            return;
        }

        long step = (width + FANOUT - 1) / FANOUT;

        Map<Long, Hash> src = calculateChildHash(sourceJdbcTemplate, result.tableName(), low, high, step);
        Map<Long, Hash> target = calculateChildHash(targetJdbcTemplate, result.tableName(), low, high, step);

        long buckets = (width + step - 1) / step;

        for (long b = 0; b < buckets; b++) {
            Hash s = src.getOrDefault(b, Hash.EMPTY);
            Hash t = target.getOrDefault(b, Hash.EMPTY);

            if (s.equals(t)) {
                continue;
            }

            long childLow = low + b * step;
            long childHigh = Math.min(high, childLow + step);
            descend(childLow, childHigh, result);
        }
    }

    private Map<Long, Hash> calculateChildHash(JdbcTemplate jdbcTemplate, String tableName,
                                               long low, long high, long step) {

        String sql = "SELECT ((t.id - ?) / ?) AS bucket, "
                + "COUNT(*) AS cnt, "
                + "COALESCE(BIT_XOR(hashtextextended(t::text, 0)), 0) AS xr "
                + "FROM " + tableName + " t "
                + "WHERE t.id >= ? AND t.id < ? "
                + "GROUP BY 1";

        Map<Long, Hash> hashes = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            hashes.put(rs.getLong("bucket"), new Hash(rs.getLong("cnt"), rs.getLong("xr")));
        }, low, step, low, high);

        return hashes;
    }

    /** Row-by-row comparison of [low, high); records differing ids into the result. */
    private void compareIndividual(long low, long high, ComparisionResult result) {

        Map<Long, Long> src = fetchRowHashes(sourceJdbcTemplate, result.tableName(), low, high);
        Map<Long, Long> target = fetchRowHashes(targetJdbcTemplate, result.tableName(), low, high);

        for (Map.Entry<Long, Long> e : src.entrySet()) {
            Long targetHash = target.get(e.getKey());
            if (targetHash == null) {
                result.missingInTarget().add(e.getKey());
            } else if (!targetHash.equals(e.getValue())) {
                result.mismatched().add(e.getKey());
            }
        }

        for (Long id : target.keySet()) {
            if (!src.containsKey(id)) {
                result.extraInTarget().add(id);
            }
        }
    }

    private Map<Long, Long> fetchRowHashes(JdbcTemplate jdbcTemplate, String tableName, long low, long high) {

        String sql = "SELECT t.id, hashtextextended(t::text, 0) AS h "
                + "FROM " + tableName + " t "
                + "WHERE t.id >= ? AND t.id < ?";

        Map<Long, Long> rows = new HashMap<>();

        jdbcTemplate.query(sql, rs -> {
            rows.put(rs.getLong("id"), rs.getLong("h"));
        }, low, high);

        return rows;
    }

    private Long[] getKeyBounds(JdbcTemplate jdbcTemplate, String tableName) {

        if (!tableName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid table name");
        }
    
        String sql = "SELECT MIN(id), MAX(id) FROM " + tableName;
    
        return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
    
            long minValue = rs.getLong(1);
            Long min = rs.wasNull() ? null : minValue;
    
            long maxValue = rs.getLong(2);
            Long max = rs.wasNull() ? null : maxValue;
    
            return new Long[]{min, max};
        });
    }

}