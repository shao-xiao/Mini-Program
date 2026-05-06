package com.dehui.property.modules.aiassistant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationQaService {

    private final DailyReportService dailyReportService;
    private final WorkOrderAnalysisService workOrderAnalysisService;
    private final ReminderService reminderService;

    // 问答入口（安全版）
    public Map<String, Object> ask(String question) {

        if (question == null || question.isBlank()) {
            return Map.of("type", "invalid", "answer", "请输入问题");
        }

        String q = question.toLowerCase();

        if (q.contains("日报") || q.contains("report")) {
            return Map.of("type", "daily_report",
                    "answer", dailyReportService.generateDailyReport());
        }

        if (q.contains("工单") || q.contains("work")) {
            return Map.of("type", "work_order",
                    "answer", workOrderAnalysisService.analyze());
        }

        if (q.contains("逾期") || q.contains("账单") || q.contains("bill")) {
            return Map.of("type", "bill",
                    "answer", reminderService.getOverdueBills());
        }

        return Map.of("type", "unknown", "answer", "暂不支持该问题");
    }

    // AI分析（老板版）
    public Map<String, Object> analysis() {

        var report = dailyReportService.generateDailyReport();
        var work = workOrderAnalysisService.analyze();
        var overdue = reminderService.getOverdueBills();

        String summary = buildSummary(report, work, overdue);

        return Map.of(
                "summary", summary,
                "report", report,
                "work", work,
                "overdue", overdue
        );
    }

    private String buildSummary(Object report,
                                Map<String, Object> work,
                                List<?> overdue) {

        StringBuilder sb = new StringBuilder();

        sb.append("园区运营情况：");

        if (!overdue.isEmpty()) {
            sb.append("存在逾期账单，需要尽快催缴；");
        }

        Object processing = work.get("processing");
        if (processing != null && processing.toString().equals("0")) {
            sb.append("当前工单压力较小；");
        }

        if (sb.length() == 6) {
            sb.append("整体运行正常；");
        }

        return sb.toString();
    }
}