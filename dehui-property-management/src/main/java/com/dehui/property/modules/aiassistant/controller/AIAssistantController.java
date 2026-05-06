package com.dehui.property.modules.aiassistant.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.aiassistant.dto.ChatRequest;
import com.dehui.property.modules.aiassistant.dto.ChatResponse;
import com.dehui.property.modules.aiassistant.dto.DailyReportResponse;
import com.dehui.property.modules.aiassistant.service.AIAssistantService;
import com.dehui.property.modules.aiassistant.service.DailyReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIAssistantController {
    private final AIAssistantService aiAssistantService;
    private final DailyReportService dailyReportService;

    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        return Result.success(aiAssistantService.chat(request));
    }

    @GetMapping("/daily-report")
    public Result<DailyReportResponse> dailyReport() {
        return Result.success(dailyReportService.generateDailyReport());
    }
}
