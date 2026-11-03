package com.example.replicator.DCT;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

import com.example.replicator.ComparisionResult;

@Component
public class DCT {

    private final JdbcTemplate sourceJdbcTemplate;
    private final JdbcTemplate targetJdbcTemplate;
    private final RowHashRepository rowHashRepository;

    public DCT( @Qualifier("sourceJdbcTemplate") JdbcTemplate sourceJdbcTemplate, @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate){
        this.sourceJdbcTemplate = sourceJdbcTemplate;
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.rowHashRepository = new RowHashRepository(this.sourceJdbcTemplate,this.targetJdbcTemplate);
    }

    public ComparisionResult compareData(String tableName){
        
        List<RowHashRepository.RowHashResult> sourceRowHash = rowHashRepository.getSourceHashes(tableName);
        List<RowHashRepository.RowHashResult> targetRowHash = rowHashRepository.getTargetHashes(tableName);

        List<Long> missingInTarget = new ArrayList<>();
        List<Long> extraInTarget = new ArrayList<>();
        List<Long> mismatched = new ArrayList<>();

        Map<Long, String> sourceMap = toHashMap(sourceRowHash);
        Map<Long, String> targetMap = toHashMap(targetRowHash);

        for(Map.Entry<Long,String> entry : sourceMap.entrySet()){
            Long id = entry.getKey();
            String sourceHash = entry.getValue();

            if (!targetMap.containsKey(id)){
                missingInTarget.add(id);
            }
            else if(!Objects.equals(sourceHash, targetMap.get(id))){
                mismatched.add(id);
            }
        }

        for(Map.Entry<Long,String> entry : targetMap.entrySet()){
            Long id = entry.getKey();

            if(!sourceMap.containsKey(id)){
                extraInTarget.add(id);
            }
        }

        return new ComparisionResult(
            tableName,
            missingInTarget,
            extraInTarget,
            mismatched
        );
    }

    

    private Map<Long, String> toHashMap( List<RowHashRepository.RowHashResult> rowHashes) {

        Map<Long, String> hashMap = new HashMap<>();

        for (RowHashRepository.RowHashResult row : rowHashes) {
            hashMap.put(row.id(), row.rowHash());
        }

        return hashMap;
    }

    public void printComparisionResults(ComparisionResult result) {

        System.out.println("\n========== DCT Comparison Results ==========");
        System.out.println("Table: " + result.tableName());
    
        System.out.println("\n--- Missing in Target ---");
    
        if (result.missingInTarget().isEmpty()) {
            System.out.println("No missing records.");
        } else {
            for (Long id : result.missingInTarget()) {
                Map<String, Object> record =
                rowHashRepository.getRecord(sourceJdbcTemplate, result.tableName(), id);
    
                System.out.println("ID: " + id);
                System.out.println("Full Record: " + record);
            }
        }
    
        System.out.println("\n--- Extra in Target ---");
    
        if (result.extraInTarget().isEmpty()) {
            System.out.println("No extra records.");
        } else {
            for (Long id : result.extraInTarget()) {
                Map<String, Object> record =
                rowHashRepository.getRecord(targetJdbcTemplate, result.tableName(), id);
    
                System.out.println("ID: " + id);
                System.out.println("Full Record: " + record);
            }
        }
    
        System.out.println("\n--- Mismatched Records ---");
    
        if (result.mismatched().isEmpty()) {
            System.out.println("No mismatched records.");
        } else {
            for (Long id : result.mismatched()) {
                Map<String, Object> sourceRecord =
                rowHashRepository.getRecord(sourceJdbcTemplate, result.tableName(), id);
    
                Map<String, Object> targetRecord =
                rowHashRepository.getRecord(targetJdbcTemplate, result.tableName(), id);
    
                System.out.println("\nID: " + id);
                System.out.println("Source Record: " + sourceRecord);
                System.out.println("Target Record: " + targetRecord);
            }
        }
    }
    
}
