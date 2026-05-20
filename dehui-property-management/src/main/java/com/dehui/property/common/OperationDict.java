package com.dehui.property.common;

import java.util.Map;

public final class OperationDict {
    private static final Map<String, String> WORK_ORDER_STATUS_ALIASES = Map.ofEntries(
            Map.entry("CREATED", "PENDING_ASSIGN"),
            Map.entry("待派单", "PENDING_ASSIGN"),
            Map.entry("已创建", "PENDING_ASSIGN"),
            Map.entry("ASSIGNED", "ASSIGNED"),
            Map.entry("已派单", "ASSIGNED"),
            Map.entry("PROCESSING", "PROCESSING"),
            Map.entry("处理中", "PROCESSING"),
            Map.entry("PENDING_CONFIRM", "PENDING_CONFIRM"),
            Map.entry("待确认", "PENDING_CONFIRM"),
            Map.entry("COMPLETED", "COMPLETED"),
            Map.entry("已完成", "COMPLETED"),
            Map.entry("CLOSED", "CLOSED"),
            Map.entry("已关闭", "CLOSED"),
            Map.entry("CANCELLED", "WITHDRAWN"),
            Map.entry("WITHDRAWN", "WITHDRAWN"),
            Map.entry("已撤回", "WITHDRAWN"),
            Map.entry("已取消", "WITHDRAWN")
    );

    private static final Map<String, String> INSPECTION_STATUS_ALIASES = Map.ofEntries(
            Map.entry("OPEN", "OPEN"),
            Map.entry("未关闭", "OPEN"),
            Map.entry("未开始", "OPEN"),
            Map.entry("进行中", "OPEN"),
            Map.entry("CLOSED", "CLOSED"),
            Map.entry("已关闭", "CLOSED"),
            Map.entry("已完成", "CLOSED")
    );

    private static final Map<String, String> INSPECTION_RESULT_ALIASES = Map.ofEntries(
            Map.entry("NORMAL", "NORMAL"),
            Map.entry("正常", "NORMAL"),
            Map.entry("ABNORMAL", "ABNORMAL"),
            Map.entry("异常", "ABNORMAL")
    );

    private static final Map<String, String> VISITOR_STATUS_ALIASES = Map.ofEntries(
            Map.entry("PENDING_REVIEW", "PENDING_REVIEW"),
            Map.entry("待审核", "PENDING_REVIEW"),
            Map.entry("REGISTERED", "REGISTERED"),
            Map.entry("已登记", "REGISTERED"),
            Map.entry("ENTERED", "ENTERED"),
            Map.entry("已进入", "ENTERED"),
            Map.entry("已入场", "ENTERED"),
            Map.entry("LEFT", "LEFT"),
            Map.entry("已离场", "LEFT"),
            Map.entry("已离开", "LEFT"),
            Map.entry("CANCELLED", "CANCELLED"),
            Map.entry("已取消", "CANCELLED"),
            Map.entry("REJECTED", "REJECTED"),
            Map.entry("已拒绝", "REJECTED")
    );

    private static final Map<String, String> ANNOUNCEMENT_STATUS_ALIASES = Map.ofEntries(
            Map.entry("DRAFT", "DRAFT"),
            Map.entry("草稿", "DRAFT"),
            Map.entry("PUBLISHED", "PUBLISHED"),
            Map.entry("已发布", "PUBLISHED"),
            Map.entry("ARCHIVED", "OFFLINE"),
            Map.entry("OFFLINE", "OFFLINE"),
            Map.entry("已归档", "OFFLINE"),
            Map.entry("已下架", "OFFLINE")
    );

    private static final Map<String, String> ANNOUNCEMENT_TYPE_ALIASES = Map.ofEntries(
            Map.entry("NOTICE", "NOTICE"),
            Map.entry("通知", "NOTICE"),
            Map.entry("MAINTENANCE", "NOTICE"),
            Map.entry("OUTAGE", "OUTAGE"),
            Map.entry("停水停电", "OUTAGE"),
            Map.entry("PAYMENT_REMINDER", "PAYMENT_REMINDER"),
            Map.entry("PAYMENT", "PAYMENT_REMINDER"),
            Map.entry("缴费提醒", "PAYMENT_REMINDER"),
            Map.entry("EVENT", "EVENT"),
            Map.entry("活动通知", "EVENT"),
            Map.entry("SYSTEM", "SYSTEM"),
            Map.entry("系统公告", "SYSTEM")
    );

    private OperationDict() {
    }

    public static String workOrderStatus(String value) {
        return normalize(value, "PENDING_ASSIGN", WORK_ORDER_STATUS_ALIASES);
    }

    public static String workOrderStatusLabel(String value) {
        return switch (workOrderStatus(value)) {
            case "PENDING_ASSIGN" -> "待派单";
            case "ASSIGNED" -> "已派单";
            case "PROCESSING" -> "处理中";
            case "PENDING_CONFIRM" -> "待确认";
            case "COMPLETED" -> "已完成";
            case "CLOSED" -> "已关闭";
            case "WITHDRAWN" -> "已撤回";
            default -> safe(value);
        };
    }

    public static String inspectionStatus(String value) {
        return normalize(value, "OPEN", INSPECTION_STATUS_ALIASES);
    }

    public static String inspectionStatusLabel(String value) {
        return "CLOSED".equals(inspectionStatus(value)) ? "已关闭" : "未关闭";
    }

    public static String inspectionResult(String value) {
        return normalize(value, "NORMAL", INSPECTION_RESULT_ALIASES);
    }

    public static String inspectionResultLabel(String value) {
        return "ABNORMAL".equals(inspectionResult(value)) ? "异常" : "正常";
    }

    public static String inspectionTypeLabel(String value) {
        return switch (safe(value)) {
            case "FIRE" -> "消防巡检";
            case "SECURITY" -> "安防巡检";
            case "WEAK_CURRENT" -> "弱电巡检";
            case "HVAC" -> "空调巡检";
            case "ELEVATOR" -> "电梯巡检";
            case "PUBLIC_FACILITY" -> "公共设施巡检";
            case "ENVIRONMENT" -> "环境卫生巡检";
            default -> safe(value);
        };
    }

    public static String visitorStatus(String value) {
        return normalize(value, "PENDING_REVIEW", VISITOR_STATUS_ALIASES);
    }

    public static String visitorStatusLabel(String value) {
        return switch (visitorStatus(value)) {
            case "PENDING_REVIEW" -> "待审核";
            case "REGISTERED" -> "已登记";
            case "ENTERED" -> "已入场";
            case "LEFT" -> "已离场";
            case "CANCELLED" -> "已取消";
            case "REJECTED" -> "已拒绝";
            default -> safe(value);
        };
    }

    public static String announcementStatus(String value) {
        return normalize(value, "DRAFT", ANNOUNCEMENT_STATUS_ALIASES);
    }

    public static String announcementType(String value) {
        return normalize(value, "NOTICE", ANNOUNCEMENT_TYPE_ALIASES);
    }

    public static String announcementStatusLabel(String value) {
        return switch (announcementStatus(value)) {
            case "PUBLISHED" -> "已发布";
            case "OFFLINE" -> "已下架";
            default -> "草稿";
        };
    }

    public static String announcementTypeLabel(String value) {
        return switch (announcementType(value)) {
            case "NOTICE" -> "通知";
            case "OUTAGE" -> "停水停电";
            case "PAYMENT_REMINDER" -> "缴费提醒";
            case "EVENT" -> "活动通知";
            case "SYSTEM" -> "系统公告";
            default -> safe(value);
        };
    }

    public static String announcementTarget(String value) {
        String target = safe(value).toUpperCase();
        return switch (target) {
            case "TENANT", "FLOOR", "ROOM" -> target;
            default -> "ALL_TENANTS";
        };
    }

    public static String announcementTargetLabel(String value) {
        return switch (announcementTarget(value)) {
            case "TENANT" -> "指定租户";
            case "FLOOR" -> "指定楼层";
            case "ROOM" -> "指定房间";
            default -> "全部租户";
        };
    }

    public static String fileTypeFromMime(String mimeType) {
        String value = safe(mimeType).toLowerCase();
        if (value.startsWith("image/")) {
            return "IMAGE";
        }
        if (value.contains("pdf")) {
            return "PDF";
        }
        if (value.contains("spreadsheet") || value.contains("excel")) {
            return "XLSX";
        }
        if (value.contains("word")) {
            return "DOC";
        }
        return "FILE";
    }

    private static String normalize(String value, String defaultValue, Map<String, String> aliases) {
        String safeValue = safe(value);
        if (safeValue.isBlank()) {
            return defaultValue;
        }
        String upper = safeValue.toUpperCase();
        return aliases.getOrDefault(safeValue, aliases.getOrDefault(upper, upper));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
