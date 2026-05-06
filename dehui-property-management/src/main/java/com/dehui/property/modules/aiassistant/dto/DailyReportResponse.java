package com.dehui.property.modules.aiassistant.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class DailyReportResponse {
    // 基础信息
    private LocalDate reportDate;
    private LocalDateTime generatedTime;

    // 房源概况
    private Long buildingCount;
    private Long floorCount;
    private Long roomCount;
    private Long availableRoomCount;
    private Long rentedRoomCount;

    // 租赁经营
    private Long tenantCount;
    private Long activeContractCount;
    private Long unpaidBillCount;
    private Long paidBillCount;
    private BigDecimal todayPaidAmount;

    // 工单运营
    private Long todayNewWorkOrderCount;
    private Long processingWorkOrderCount;
    private Long completedWorkOrderCount;
    private Long highPriorityWorkOrderCount;

    // 设备巡检
    private Long equipmentTotalCount;
    private Long faultEquipmentCount;
    private Long todayInspectionCount;
    private Long abnormalInspectionCount;

    // 中文日报摘要
    private String summary;
}