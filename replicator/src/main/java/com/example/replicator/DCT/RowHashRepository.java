package com.example.replicator.DCT;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class RowHashRepository {

    private final JdbcTemplate sourceJdbcTemplate;
    private final JdbcTemplate targetJdbcTemplate;

    public RowHashRepository(JdbcTemplate sourceJdbcTemplate , JdbcTemplate targetJdbcTemplate) {
        this.sourceJdbcTemplate = sourceJdbcTemplate;
        this.targetJdbcTemplate = targetJdbcTemplate;
    }

    public record RowHashResult(int id, String rowHash) {}

    public List<RowHashResult> getSourceHashes(String table) {
        return getHashes(sourceJdbcTemplate,table);
    }

    public List<RowHashResult> getTargetHashes(String table) {
        return getHashes(targetJdbcTemplate,table);
    }

    private List<RowHashResult> getHashes(JdbcTemplate jdbcTemplate,String tableName) {
        String sql = """
            SELECT id, md5(row(t.*)::text) AS row_hash
            FROM %s t
            """;
        sql = String.format(sql, tableName);
        
        return jdbcTemplate.query(sql, (rs, rowNum) ->
            new RowHashResult(
                rs.getInt("id"),
                rs.getString("row_hash")
            )
        );
    }

    public Map<String, Object> getRecord(
            JdbcTemplate jdbcTemplate,
            String tableName,
            Integer id) {

        String sql = "SELECT * FROM " + tableName + " WHERE id = ?";

        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(sql, id);

        return rows.isEmpty() ? null : rows.get(0);
    }
}