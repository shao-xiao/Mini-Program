package com.dehui.property.modules.aiassistant.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.aiassistant.service.WorkOrderAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/ai/workorders")
@RequiredArgsConstructor
public class WorkOrderAnalysisController {

    private final WorkOrderAnalysisService workOrderAnalysisService;

    @GetMapping("/analysis")
    public Result<Map<String, Object>> analysis() {
        return Result.success(workOrderAnalysisService.analyze());
    }
}