package com.example.replicator;

import org.springframework.stereotype.Service;
import com.example.replicator.DCT.DCT;
import com.example.replicator.seeding.Seeding;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class SeedingService {

    private final DCT dct;
    private final Seeding seeding;
    private final KafkaListenerService kafkaListenerService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private String table;

    public SeedingService(DCT dct, Seeding seeding,KafkaListenerService kafkaListenerService) {
        this.dct = dct;
        this.seeding = seeding;
        this.kafkaListenerService = kafkaListenerService;

    }

    public DCT.ComparisonResult compare() {
        return dct.compareData(table);
    }

    public void seed(DCT.ComparisonResult result) {
        seeding.seed(result);
    }

    public void setTable(String table) {
        this.table = table;
    }

    public void scheduleSeed(long initialDelayMinutes, long repeatIntervalMinutes){

        if (initialDelayMinutes < 0 || repeatIntervalMinutes <= 0) {
            throw new IllegalArgumentException(
                    "Initial delay must be >= 0 and repeat interval must be > 0"
            );
        }

        scheduler.scheduleAtFixedRate(()->{

            kafkaListenerService.stop();
            System.out.println("Kafka listener stopped.");
            try{
                DCT.ComparisonResult result = compare();

                seed(result);

            }
            catch(Exception e){
                e.printStackTrace();
            }

        }, initialDelayMinutes, repeatIntervalMinutes, TimeUnit.MINUTES);
        
    }
}