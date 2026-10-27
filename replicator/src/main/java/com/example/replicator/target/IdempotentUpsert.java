package com.example.replicator.target;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class IdempotentUpsert {
    
    private final JdbcTemplate jdbcTemplate;

    public IdempotentUpsert( @Qualifier("targetJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(transactionManager = "targetTransactionManager")
    public boolean canProcessEvent(ConsumerRecord<String, String> record){
        
        String topic = record.topic();
        
        int partition = record.partition();
        
        long offset = record.offset();
        
        OffsetDateTime timestamp = setProcessedAtFromLong(record.timestamp());

        int rowsInserted = jdbcTemplate.update(
            """
            INSERT INTO processed_events (topic,partition_id,offset_value,processed_at)
            VALUES (?,?,?,?)
            ON CONFLICT DO NOTHING
            """,
            topic,partition,offset,timestamp
        );

        return rowsInserted != 0;

    }

    public OffsetDateTime setProcessedAtFromLong(long timestamp) {
        return OffsetDateTime.ofInstant(
            Instant.ofEpochMilli(timestamp), 
            ZoneOffset.UTC
        );
    }


}
