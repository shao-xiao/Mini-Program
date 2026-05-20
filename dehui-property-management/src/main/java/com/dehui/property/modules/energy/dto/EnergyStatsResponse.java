package com.dehui.property.modules.energy.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EnergyStatsResponse {
    private long recordCount;
    private BigDecimal electricUsage;
    private BigDecimal waterUsage;
    private BigDecimal gasUsage;
    private BigDecimal totalAmount;
    private BigDecimal averageUsage;
    private long abnormalCount;
    private List<TrendItem> trend;
    private List<TypeStructureItem> typeStructure;
    private List<RankingItem> roomRanking;
    private List<RankingItem> buildingRanking;
    private List<AnomalyItem> anomalies;

    @Data
    public static class TrendItem {
        private String periodMonth;
        private BigDecimal electricUsage;
        private BigDecimal waterUsage;
        private BigDecimal gasUsage;
        private BigDecimal settlementAmount;
    }

    @Data
    public static class TypeStructureItem {
        private String meterType;
        private BigDecimal usageAmount;
        private BigDecimal settlementAmount;
    }

    @Data
    public static class RankingItem {
        private Long id;
        private String name;
        private String buildingName;
        private String roomName;
        private BigDecimal usageAmount;
        private BigDecimal settlementAmount;
    }

    @Data
    public static class AnomalyItem {
        private Long id;
        private String meterNo;
        private String meterType;
        private String periodMonth;
        private String buildingName;
        private String roomName;
        private BigDecimal usageAmount;
        private String abnormalReason;
        private String abnormalStatus;
    }
}
