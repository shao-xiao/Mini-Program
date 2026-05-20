package com.dehui.property.modules.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class IdListRequest {
    private List<Long> ids;
}
