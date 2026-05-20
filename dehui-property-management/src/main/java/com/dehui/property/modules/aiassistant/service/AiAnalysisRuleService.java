package com.dehui.property.modules.aiassistant.service;

import com.dehui.property.modules.aiassistant.dto.AiActionItemDTO;
import com.dehui.property.modules.aiassistant.dto.AiRiskItemDTO;
import com.dehui.property.modules.aiassistant.entity.AiDailyReport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiAnalysisRuleService {
    public List<AiRiskItemDTO> buildRiskItems(AiDailyReport report, long expiringContractCount) {
        List<AiRiskItemDTO> items = new ArrayList<>();

        if (value(report.getUnpaidBillCount()) > 0) {
            items.add(new AiRiskItemDTO(
                    "UNPAID_BILL",
                    "WARNING",
                    "存在未支付账单",
                    "当前有" + value(report.getUnpaidBillCount()) + "笔账单未支付，建议财务人员跟进催缴。",
                    "finance",
                    "/bills?status=UNPAID"
            ));
        }
        if (value(report.getOverdueBillCount()) > 0) {
            items.add(new AiRiskItemDTO(
                    "OVERDUE_BILL",
                    "CRITICAL",
                    "存在逾期账单",
                    "当前有" + value(report.getOverdueBillCount()) + "笔账单已逾期，请优先核对并催缴。",
                    "finance",
                    "/bills?status=OVERDUE"
            ));
        }
        if (value(report.getHighPriorityWorkOrderCount()) > 0) {
            items.add(new AiRiskItemDTO(
                    "HIGH_PRIORITY_WORK_ORDER",
                    "WARNING",
                    "存在高优先级工单",
                    "当前有" + value(report.getHighPriorityWorkOrderCount()) + "个高优先级工单，建议工程人员优先处理。",
                    "work-order",
                    "/workorders?priority=HIGH"
            ));
        }
        if (value(report.getOverdueWorkOrderCount()) > 0) {
            items.add(new AiRiskItemDTO(
                    "OVERDUE_WORK_ORDER",
                    "CRITICAL",
                    "存在超时工单",
                    "当前有" + value(report.getOverdueWorkOrderCount()) + "个工单超时未完成，请立即跟进处理。",
                    "work-order",
                    "/workorders"
            ));
        }
        if (value(report.getFaultDeviceCount()) > 0) {
            items.add(new AiRiskItemDTO(
                    "FAULT_DEVICE",
                    "WARNING",
                    "存在故障设备",
                    "当前有" + value(report.getFaultDeviceCount()) + "台设备处于故障状态，建议安排检修。",
                    "equipment",
                    "/equipment"
            ));
        }
        if (value(report.getAbnormalInspectionCount()) > 0) {
            items.add(new AiRiskItemDTO(
                    "ABNORMAL_INSPECTION",
                    "WARNING",
                    "存在异常巡检",
                    "当前有" + value(report.getAbnormalInspectionCount()) + "条异常巡检记录，建议运营人员复核。",
                    "inspection",
                    "/inspections"
            ));
        }
        if (decimal(report.getOccupancyRate()).compareTo(new BigDecimal("60.00")) < 0) {
            items.add(new AiRiskItemDTO(
                    "LOW_OCCUPANCY_RATE",
                    "WARNING",
                    "出租率低于目标",
                    "当前出租率为" + decimal(report.getOccupancyRate()) + "%，低于60%，建议加强招商跟进。",
                    "room",
                    "/rooms"
            ));
        }
        if (expiringContractCount > 0) {
            items.add(new AiRiskItemDTO(
                    "CONTRACT_EXPIRING",
                    "WARNING",
                    "合同即将到期",
                    "未来30天内有" + expiringContractCount + "份合同到期，建议提前沟通续约。",
                    "contract",
                    "/contracts"
            ));
        }

        return items;
    }

    public List<AiActionItemDTO> buildActionItems(AiDailyReport report) {
        List<AiActionItemDTO> items = new ArrayList<>();

        if (value(report.getOverdueBillCount()) > 0) {
            items.add(new AiActionItemDTO("HIGH", "优先催缴逾期账单",
                    "当前有" + value(report.getOverdueBillCount()) + "笔逾期账单，请财务人员优先处理。",
                    "finance", "/bills?status=OVERDUE"));
        }
        if (value(report.getHighPriorityWorkOrderCount()) > 0) {
            items.add(new AiActionItemDTO("HIGH", "优先处理高优先级工单",
                    "当前有" + value(report.getHighPriorityWorkOrderCount()) + "个高优先级工单，请安排工程人员处理。",
                    "work-order", "/workorders?priority=HIGH"));
        }
        if (value(report.getOverdueWorkOrderCount()) > 0) {
            items.add(new AiActionItemDTO("HIGH", "处理超时工单",
                    "当前有" + value(report.getOverdueWorkOrderCount()) + "个超时工单，请确认责任人和处理进度。",
                    "work-order", "/workorders"));
        }
        if (value(report.getUnpaidBillCount()) > 0) {
            items.add(new AiActionItemDTO("MEDIUM", "跟进未支付账单",
                    "当前有" + value(report.getUnpaidBillCount()) + "笔未支付账单，请财务人员核对并催缴。",
                    "finance", "/bills?status=UNPAID"));
        }
        if (value(report.getFaultDeviceCount()) > 0) {
            items.add(new AiActionItemDTO("MEDIUM", "安排故障设备检修",
                    "当前有" + value(report.getFaultDeviceCount()) + "台故障设备，请安排检修并更新状态。",
                    "equipment", "/equipment"));
        }
        if (value(report.getAbnormalInspectionCount()) > 0) {
            items.add(new AiActionItemDTO("MEDIUM", "复核异常巡检",
                    "当前有" + value(report.getAbnormalInspectionCount()) + "条异常巡检记录，请运营人员复核。",
                    "inspection", "/inspections"));
        }
        if (decimal(report.getOccupancyRate()).compareTo(new BigDecimal("60.00")) < 0) {
            items.add(new AiActionItemDTO("LOW", "提升招商转化",
                    "当前出租率低于60%，建议跟进招商线索并梳理可租房源。",
                    "room", "/rooms"));
        }
        if (value(report.getRoomAvailable()) > 0) {
            items.add(new AiActionItemDTO("LOW", "盘点可租房间",
                    "当前有" + value(report.getRoomAvailable()) + "间可租房间，建议更新房源信息。",
                    "room", "/rooms"));
        }

        return items;
    }

    public String resolveRiskLevel(List<AiRiskItemDTO> risks) {
        boolean critical = risks.stream().anyMatch(item -> "CRITICAL".equals(item.getLevel()));
        if (critical) {
            return "CRITICAL";
        }
        boolean warning = risks.stream().anyMatch(item -> "WARNING".equals(item.getLevel()));
        return warning ? "WARNING" : "NORMAL";
    }

    public String buildSummary(AiDailyReport report) {
        String suggestion;
        if (value(report.getOverdueBillCount()) > 0 || value(report.getOverdueWorkOrderCount()) > 0) {
            suggestion = "建议优先处理逾期账单和超时工单。";
        } else if (value(report.getUnpaidBillCount()) > 0 || value(report.getHighPriorityWorkOrderCount()) > 0) {
            suggestion = "建议优先跟进未支付账单和高优先级工单。";
        } else if (decimal(report.getOccupancyRate()).compareTo(new BigDecimal("60.00")) < 0) {
            suggestion = "建议加强招商跟进并提升房源转化。";
        } else {
            suggestion = "整体运营状态平稳，建议保持日常巡检与账单跟进节奏。";
        }

        return "当前房间共" + value(report.getRoomTotal())
                + "间，已出租" + value(report.getRoomRented())
                + "间，出租率" + decimal(report.getOccupancyRate()) + "%；未支付账单"
                + value(report.getUnpaidBillCount()) + "笔，今日收款¥"
                + money(report.getTodayIncomeAmount()) + "；处理中工单"
                + value(report.getProcessingWorkOrderCount()) + "个，其中高优先级"
                + value(report.getHighPriorityWorkOrderCount()) + "个。" + suggestion;
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String money(BigDecimal value) {
        return decimal(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
