package com.dehui.property.modules.aiassistant.service;

import com.dehui.property.modules.aiassistant.dto.ChatRequest;
import com.dehui.property.modules.aiassistant.dto.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIAssistantService {

    public ChatResponse chat(ChatRequest request) {
        log.info("AI chat request: {}", request.getMessage());
        return new ChatResponse(
            "您好，我是AI物业助手，当前功能正在开发中，请稍候。",
            request.getSessionId(),
            "[]"
        );
    }
}
