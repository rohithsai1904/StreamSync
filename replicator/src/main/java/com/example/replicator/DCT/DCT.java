package com.example.replicator.DCT;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DCT {

    private final JdbcTemplate sourceJdbcTemplate;
    private final JdbcTemplate targetJdbcTemplate;
    private final RowHashRepository rowHashRepository;

    private List<Integer> missingInTarget = new ArrayList<>();
    private List<Integer> extraInTarget = new ArrayList<>();
    private List<Integer> mismatched = new ArrayList<>();


    public DCT( @Qualifier("sourceJdbcTemplate") JdbcTemplate sourceJdbcTemplate, @Qualifier("targetJdbcTemplate") JdbcTemplate targetJdbcTemplate){
        this.sourceJdbcTemplate = sourceJdbcTemplate;
        this.targetJdbcTemplate = targetJdbcTemplate;
        this.rowHashRepository = new RowHashRepository(this.sourceJdbcTemplate,this.targetJdbcTemplate);
    }

    public record ComparisonResult(
        String tableName,
        List<Integer> missingInTarget,
        List<Integer> extraInTarget,
        List<Integer> mismatched
    ) {
        public ComparisonResult {
            missingInTarget = List.copyOf(missingInTarget);
            extraInTarget = List.copyOf(extraInTarget);
            mismatched = List.copyOf(mismatched);
        }
    }

    public ComparisonResult compareData(String tableName){
        
        List<RowHashRepository.RowHashResult> sourceRowHash = rowHashRepository.getSourceHashes(tableName);
        List<RowHashRepository.RowHashResult> targetRowHash = rowHashRepository.getTargetHashes(tableName);

        missingInTarget = new ArrayList<>();
        extraInTarget = new ArrayList<>();
        mismatched = new ArrayList<>();

        Map<Integer, String> sourceMap = toHashMap(sourceRowHash);
        Map<Integer, String> targetMap = toHashMap(targetRowHash);

        for(Map.Entry<Integer,String> entry : sourceMap.entrySet()){
            Integer id = entry.getKey();
            String sourceHash = entry.getValue();

            if (!targetMap.containsKey(id)){
                missingInTarget.add(id);
            }
            else if(!Objects.equals(sourceHash, targetMap.get(id))){
                mismatched.add(id);
            }
        }

        for(Map.Entry<Integer,String> entry : targetMap.entrySet()){
            Integer id = entry.getKey();

            if(!sourceMap.containsKey(id)){
                extraInTarget.add(id);
            }
        }

        return new ComparisonResult(
            tableName,
            missingInTarget,
            extraInTarget,
            mismatched
        );
    }

    

    private Map<Integer, String> toHashMap( List<RowHashRepository.RowHashResult> rowHashes) {

        Map<Integer, String> hashMap = new HashMap<>();

        for (RowHashRepository.RowHashResult row : rowHashes) {
            hashMap.put(row.id(), row.rowHash());
        }

        return hashMap;
    }

    public void printComparisonResults(ComparisonResult result) {

        System.out.println("\n========== DCT Comparison Results ==========");
        System.out.println("Table: " + result.tableName());
    
        System.out.println("\n--- Missing in Target ---");
    
        if (result.missingInTarget().isEmpty()) {
            System.out.println("No missing records.");
        } else {
            for (Integer id : result.missingInTarget()) {
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
            for (Integer id : result.extraInTarget()) {
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
            for (Integer id : result.mismatched()) {
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
