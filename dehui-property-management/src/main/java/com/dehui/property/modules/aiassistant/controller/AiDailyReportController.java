package com.dehui.property.modules.aiassistant.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.aiassistant.dto.AiDailyReportDTO;
import com.dehui.property.modules.aiassistant.service.AiDailyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/ai/daily-report")
@RequiredArgsConstructor
public class AiDailyReportController {
    private final AiDailyReportService aiDailyReportService;

    @GetMapping
    public Result<AiDailyReportDTO> getDailyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(aiDailyReportService.getOrGenerate(date));
    }

    @PostMapping("/refresh")
    public Result<AiDailyReportDTO> refreshDailyReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(aiDailyReportService.refresh(date));
    }

    @GetMapping("/history")
    public Result<List<AiDailyReportDTO>> history() {
        return Result.success(aiDailyReportService.history());
    }

    @GetMapping("/{id}")
    public Result<AiDailyReportDTO> detail(@PathVariable Long id) {
        AiDailyReportDTO report = aiDailyReportService.detail(id);
        return report == null ? Result.error("日报不存在") : Result.success(report);
    }
}
