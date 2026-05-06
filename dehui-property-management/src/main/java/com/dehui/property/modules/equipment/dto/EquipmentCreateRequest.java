package com.dehui.property.modules.equipment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDate;

@Data
public class EquipmentCreateRequest {
    @NotBlank(message = "设备名称不能为空")
    private String equipmentName;

    private String equipmentCode;

    private String equipmentType;

    private String location;

    private String manufacturer;

    private String model;

    private LocalDate installDate;

    private String remark;
}