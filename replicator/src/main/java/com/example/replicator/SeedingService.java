package com.example.replicator;

import org.springframework.stereotype.Service;
import com.example.replicator.DCT.DCT;
import com.example.replicator.seeding.Seeding;

@Service
public class SeedingService {

    private final DCT dct;
    private final Seeding seeding;
    private String table;

    public SeedingService(DCT dct, Seeding seeding) {
        this.dct = dct;
        this.seeding = seeding;
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
}