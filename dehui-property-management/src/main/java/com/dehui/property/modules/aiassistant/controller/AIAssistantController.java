package com.dehui.property.modules.aiassistant.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.aiassistant.dto.ChatRequest;
import com.dehui.property.modules.aiassistant.dto.ChatResponse;
import com.dehui.property.modules.aiassistant.service.AIAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AIAssistantController {
    private final AIAssistantService aiAssistantService;

    @PostMapping("/chat")
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        return Result.success(aiAssistantService.chat(request));
    }
}
