package com.dehui.property.modules.ai.controller;

import com.dehui.property.common.ApiResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DailyReportController {

    @GetMapping("/ai/daily-report")
    public ApiResponse<Map<String, Object>> dailyReport() {
        return ApiResponse.success(buildReport(null));
    }

    @PostMapping("/ai/daily-report/refresh")
    public ApiResponse<Map<String, Object>> refreshDailyReport() {
        return ApiResponse.success(buildReport(null));
    }

    @GetMapping("/ai/daily-report/history")
    public ApiResponse<List<Map<String, Object>>> history() {
        return ApiResponse.success(List.of());
    }

    @GetMapping("/ai/daily-report/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.success(buildReport(id));
    }

    private Map<String, Object> buildReport(Long id) {
        return Map.of(
                "id", id == null ? 0L : id,
                "reportDate", LocalDate.now().toString(),
                "generatedAt", LocalDateTime.now().toString(),
                "riskLevel", "NORMAL",
                "summaryText", "Local test environment is ready. Business data is empty.",
                "metrics", emptyMetrics(),
                "riskItems", List.of(),
                "actionItems", List.of()
        );
    }

    private Map<String, Object> emptyMetrics() {
        return Map.ofEntries(
                Map.entry("roomTotal", 0),
                Map.entry("roomRented", 0),
                Map.entry("roomAvailable", 0),
                Map.entry("occupancyRate", 0),
                Map.entry("activeContractCount", 0),
                Map.entry("paidBillCount", 0),
                Map.entry("unpaidBillCount", 0),
                Map.entry("overdueBillCount", 0),
                Map.entry("todayIncomeAmount", 0),
                Map.entry("monthIncomeAmount", 0),
                Map.entry("todayWorkOrderCount", 0),
                Map.entry("processingWorkOrderCount", 0),
                Map.entry("highPriorityWorkOrderCount", 0),
                Map.entry("overdueWorkOrderCount", 0),
                Map.entry("deviceTotal", 0),
                Map.entry("faultDeviceCount", 0),
                Map.entry("abnormalInspectionCount", 0),
                Map.entry("todayInspectionCompletionRate", 0)
        );
    }
}
