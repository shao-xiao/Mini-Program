package com.dehui.property.modules.aiassistant.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class AiDailyReportDTO {
    private Long id;
    private LocalDate reportDate;
    private LocalDateTime generatedAt;
    private String riskLevel;
    private String summaryText;
    private Map<String, Object> metrics;
    private List<AiRiskItemDTO> riskItems;
    private List<AiActionItemDTO> actionItems;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
