package com.dehui.property.modules.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InternalApplicantResponse {
    private Long id;
    private String username;
    private String realName;
    private String phone;
}
