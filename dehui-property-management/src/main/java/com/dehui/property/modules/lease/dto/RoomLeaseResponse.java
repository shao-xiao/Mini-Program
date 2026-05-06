package com.dehui.property.modules.lease.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class RoomLeaseResponse {
    private Long id;
    private Long tenantId;
    private Long roomId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String remark;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}