package com.dehui.property.modules.aiassistant.entity;

import com.dehui.property.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_daily_report")
@EqualsAndHashCode(callSuper = true)
public class AiDailyReport extends BaseEntity {
    @Column(nullable = false, unique = true)
    private LocalDate reportDate;

    private LocalDateTime generatedAt;

    private Long roomTotal;
    private Long roomRented;
    private Long roomAvailable;
    private BigDecimal occupancyRate;
    private Long activeContractCount;

    private Long paidBillCount;
    private Long unpaidBillCount;
    private Long overdueBillCount;
    private BigDecimal todayIncomeAmount;
    private BigDecimal monthIncomeAmount;

    private Long todayWorkOrderCount;
    private Long processingWorkOrderCount;
    private Long highPriorityWorkOrderCount;
    private Long overdueWorkOrderCount;

    private Long deviceTotal;
    private Long faultDeviceCount;
    private Long abnormalInspectionCount;
    private BigDecimal todayInspectionCompletionRate;

    private String riskLevel;

    @Column(length = 2000)
    private String summaryText;

    @Lob
    private String riskItemsJson;

    @Lob
    private String actionItemsJson;

    @Lob
    private String metricsJson;
}
