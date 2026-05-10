package com.dehui.property.modules.mobile.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MobileAnnouncementResponse {
    private Long id;
    private String title;
    private String content;
    private String type;
    private LocalDateTime publishTime;
}
