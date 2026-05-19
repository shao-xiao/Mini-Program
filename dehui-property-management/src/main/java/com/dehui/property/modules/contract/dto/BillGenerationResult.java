package com.dehui.property.modules.contract.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BillGenerationResult {
    private int generated;
    private int skipped;
    private List<String> reasons = new ArrayList<>();

    public void addGenerated() {
        generated++;
    }

    public void addSkipped(String reason) {
        skipped++;
        reasons.add(reason);
    }

    public void merge(BillGenerationResult other) {
        if (other == null) {
            return;
        }
        generated += other.generated;
        skipped += other.skipped;
        reasons.addAll(other.reasons);
    }
}
