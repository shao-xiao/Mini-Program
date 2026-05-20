package com.dehui.property.modules.system.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MenuResponse {
    private Long id;
    private Long parentId;
    private String menuName;
    private String title;
    private String menuCode;
    private String path;
    private String component;
    private String icon;
    private Integer sortOrder;
    private Boolean visible;
    private String status;
    private List<MenuResponse> children = new ArrayList<>();
}
