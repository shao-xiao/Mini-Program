package com.dehui.property.modules.aiassistant.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {
    private String answer;
    private String sessionId;
    private String suggestActions;
}
