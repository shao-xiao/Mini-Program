package com.dehui.property.modules.aiassistant.service;

import com.dehui.property.modules.aiassistant.dto.DailyReportResponse;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.building.repository.BuildingRepository;
import com.dehui.property.modules.building.repository.FloorRepository;
import com.dehui.property.modules.building.repository.RoomRepository;
import com.dehui.property.modules.contract.repository.ContractRepository;
import com.dehui.property.modules.equipment.repository.EquipmentRepository;
import com.dehui.property.modules.finance.service.FinanceMetricsService;
import com.dehui.property.modules.inspection.repository.InspectionRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import com.dehui.property.modules.workorder.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyReportService {

    private final BuildingRepository buildingRepository;
    private final FloorRepository floorRepository;
    private final RoomRepository roomRepository;
    private final TenantRepository tenantRepository;
    private final ContractRepository contractRepository;
    private final BillRepository billRepository;
    private final FinanceMetricsService financeMetricsService;
    private final WorkOrderRepository workOrderRepository;
    private final EquipmentRepository equipmentRepository;
    private final InspectionRepository inspectionRepository;

    public DailyReportResponse generateDailyReport() {
        LocalDate today = LocalDate.now();
        DailyReportResponse report = new DailyReportResponse();

        report.setReportDate(today);
        report.setGeneratedTime(LocalDateTime.now());

        // 基础资产
        report.setBuildingCount(buildingRepository.count());
        report.setFloorCount(floorRepository.count());
        FinanceMetricsService.RoomRentalStats rentalStats = financeMetricsService.overallRoomRentalStats();
        report.setRoomCount(rentalStats.rentableRoomCount());
        report.setAvailableRoomCount(rentalStats.availableRoomCount());
        report.setRentedRoomCount(rentalStats.rentedRoomCount());
        report.setRentalRate(rentalStats.rentalRate());

        // 租户与合同
        report.setTenantCount(tenantRepository.count());
        report.setActiveContractCount(contractRepository.countByStatus("ACTIVE"));

        // 财务
        report.setUnpaidBillCount(billRepository.countByStatus("UNPAID"));
        report.setPaidBillCount(billRepository.countByStatus("PAID"));

        BigDecimal todayPaidAmount = billRepository.sumPaidAmountByDate(today);
        report.setTodayPaidAmount(todayPaidAmount == null ? BigDecimal.ZERO : todayPaidAmount);

        // 工单
        report.setTodayNewWorkOrderCount(workOrderRepository.countByStatus("CREATED"));
        report.setProcessingWorkOrderCount(workOrderRepository.countByStatus("PROCESSING"));
        report.setCompletedWorkOrderCount(workOrderRepository.countByStatus("COMPLETED"));
        report.setHighPriorityWorkOrderCount(workOrderRepository.countByPriority("HIGH"));

        // 设备
        report.setEquipmentTotalCount(equipmentRepository.count());
        report.setFaultEquipmentCount(equipmentRepository.countByStatus("FAULT"));

        // 巡检（新模块）
        report.setTodayInspectionCount(
                inspectionRepository.findAll()
                        .stream()
                        .filter(i -> today.equals(i.getInspectionDate()))
                        .count()
        );

        report.setAbnormalInspectionCount(
                inspectionRepository.findAll()
                        .stream()
                        .filter(i -> "ABNORMAL".equals(i.getResult()))
                        .count()
        );

        // 简要总结
        report.setSummary(
                "当前可出租房间" + report.getRoomCount() +
                "间，已出租" + report.getRentedRoomCount() +
                "间，未支付账单" + report.getUnpaidBillCount() +
                "笔，处理中工单" + report.getProcessingWorkOrderCount() + "个。"
        );

        log.info("运营日报已生成: {}", today);

        return report;
    }
}
