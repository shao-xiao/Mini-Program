package com.dehui.property.modules.aiassistant.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRiskItemDTO {
    private String type;
    private String level;
    private String title;
    private String description;
    private String module;
    private String targetUrl;
}
