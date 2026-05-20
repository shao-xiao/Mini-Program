package com.dehui.property.modules.parking.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ParkingBillSyncResponse {
    private int generatedCount;
    private int syncedCount;
    private int skippedCount;
    private int failedCount;
    private List<String> items = new ArrayList<>();

    public void generated(String message) {
        generatedCount++;
        items.add(message);
    }

    public void synced(String message) {
        syncedCount++;
        items.add(message);
    }

    public void skipped(String message) {
        skippedCount++;
        items.add(message);
    }

    public void failed(String message) {
        failedCount++;
        items.add(message);
    }
}
