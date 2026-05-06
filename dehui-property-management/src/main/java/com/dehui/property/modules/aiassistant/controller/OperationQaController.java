package com.dehui.property.modules.aiassistant.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.aiassistant.service.OperationQaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class OperationQaController {

    private final OperationQaService service;

    @GetMapping("/qa")
    public Result<Map<String, Object>> qa(@RequestParam String question) {
        return Result.success(service.ask(question));
    }

    @GetMapping("/analysis")
    public Result<Map<String, Object>> analysis() {
        return Result.success(service.analysis());
    }
}