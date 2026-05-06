package com.dehui.property.modules.equipment.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class EquipmentResponse {
    private Long id;
    private String equipmentName;
    private String equipmentCode;
    private String equipmentType;
    private String location;
    private String status;
    private String manufacturer;
    private String model;
    private LocalDate installDate;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}