package com.dehui.property.modules.aiassistant.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String message;
    private String sessionId;
    private String userId;
}
