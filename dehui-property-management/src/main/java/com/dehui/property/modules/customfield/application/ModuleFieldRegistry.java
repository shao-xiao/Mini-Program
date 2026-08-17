package com.dehui.property.modules.customfield.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ModuleFieldRegistry {

    // Legacy wire aliases remain read-compatible only:
    // "reporterPhone", "联系电话", "reporter_phone", "visitCompany", "visitReason"
    // inspection: "location", "area"; "inspectionObject", "target";
    // "handlingMeasures", "action_taken". Canonical writes use the new field keys.

    private static final Set<String> SURFACES = Set.of(
            "adminList", "adminForm", "adminDetail", "miniProgramForm", "miniProgramDetail"
    );

    private final Map<String, List<Map<String, Object>>> fields;

    public ModuleFieldRegistry() {
        Map<String, List<Map<String, Object>>> definitions = new LinkedHashMap<>();
        definitions.put("workorders", workOrderFields());
        definitions.put("inspections", inspectionFields());
        definitions.put("visitors", visitorFields());
        definitions.put("rooms", roomFields());
        definitions.put("contracts", contractFields());
        definitions.put("bills", billFields());
        definitions.put("equipment", equipmentFields());
        definitions.put("energy-meters", energyMeterFields());
        definitions.put("energy-records", energyReadingFields());
        definitions.put("parking-spaces", parkingSpaceFields());
        definitions.put("parking-assignments", parkingAssignmentFields());
        definitions.put("meeting-rooms", meetingRoomFields());
        definitions.put("meeting-bookings", meetingBookingFields());
        definitions.put("investment-contents", investmentContentFields());
        definitions.put("investment-listings", investmentListingFields());
        definitions.put("investment-leads", investmentLeadFields());
        definitions.put("announcements", announcementFields());
        fields = Map.copyOf(definitions);
        validate();
    }

    public String canonicalModuleKey(String rawModuleKey) {
        String value = String.valueOf(rawModuleKey == null ? "" : rawModuleKey).trim();
        return switch (value) {
            case "workorder", "work-order" -> "workorders";
            case "inspection" -> "inspections";
            case "visitor" -> "visitors";
            case "room" -> "rooms";
            case "contract" -> "contracts";
            case "bill" -> "bills";
            case "equipments" -> "equipment";
            case "energy-meter" -> "energy-meters";
            case "energy-reading", "energy-readings" -> "energy-records";
            case "parking-space" -> "parking-spaces";
            case "parking-assignment" -> "parking-assignments";
            case "meeting-room" -> "meeting-rooms";
            case "meeting-booking" -> "meeting-bookings";
            case "investment-showcase", "investment-content" -> "investment-contents";
            case "investment-listing" -> "investment-listings";
            case "investment-lead", "investment-appointment" -> "investment-leads";
            case "announcement", "notice" -> "announcements";
            default -> value;
        };
    }

    public List<Map<String, Object>> systemFields(String moduleKey) {
        return fields.getOrDefault(canonicalModuleKey(moduleKey), List.of());
    }

    public Set<String> registeredModules() {
        return fields.keySet();
    }

    private void validate() {
        fields.forEach((moduleKey, definitions) -> {
            Set<String> keys = new java.util.HashSet<>();
            definitions.forEach(definition -> {
                String fieldKey = String.valueOf(definition.get("fieldKey"));
                if (!keys.add(fieldKey)) {
                    throw new IllegalStateException("Duplicate module fieldKey: " + moduleKey + "." + fieldKey);
                }
                if (String.valueOf(definition.get("apiField")).isBlank()) {
                    throw new IllegalStateException("Missing apiField: " + moduleKey + "." + fieldKey);
                }
            });
        });
    }

    private static List<Map<String, Object>> workOrderFields() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(field("code", "工单编号", "TEXT", "code", "code", false, true, 1));
        Map<String, Object> title = field("title", "工单标题", "TEXT", "title", "title", true, false, 2);
        configureSurface(title, "miniProgramForm", false, false, 20);
        result.add(title);
        Map<String, Object> orderType = select("orderType", "工单类型", "orderType", "order_type", true, 3, List.of(
                option("REPAIR", "维修报修"), option("PATROL", "巡检任务"),
                option("CLEAN", "保洁服务"), option("SECURITY", "安保事件")
        ));
        configureSurface(orderType, "miniProgramForm", false, false, 21);
        result.add(orderType);
        Map<String, Object> problemCategory = select("problemCategory", "问题类型", "problemCategory", "category", true, 4, List.of(
                option("WATER", "水路"), option("ELECTRIC", "电路"),
                option("AIR_CONDITIONER", "空调"), option("DOOR_WINDOW", "门窗"),
                option("NETWORK", "网络"), option("CLEANING", "保洁"), option("OTHER", "其他")
        ));
        configureSurface(problemCategory, "miniProgramForm", true, true, 4);
        result.add(problemCategory);
        result.add(select("status", "状态", "status", "work_status", false, 5, List.of(
                option("CREATED", "待处理"), option("SUBMITTED", "待处理"),
                option("ASSIGNED", "已派单"), option("PROCESSING", "处理中"),
                option("COMPLETED", "已完成"), option("CLOSED", "已关闭"),
                option("CANCELLED", "已撤回")
        ), true));
        Map<String, Object> priority = select("priority", "优先级", "priority", "priority", true, 6, List.of(
                option("NORMAL", "普通"), option("HIGH", "较高"), option("URGENT", "紧急")
        ));
        configureSurface(priority, "miniProgramForm", false, false, 22);
        result.add(priority);
        Map<String, Object> contactName = field(
                "contactName", "报修人", "TEXT", "reporterName", "reporter_name", true, false, 7
        );
        configureSurface(contactName, "adminForm", false, false, 7);
        configureSurface(contactName, "miniProgramForm", true, true, 1);
        configureValidation(contactName, Map.of("maxLength", 64));
        result.add(contactName);
        result.add(field("contactPhone", "联系电话", "PHONE", "contactPhone", "reporter_phone", false, true, 8));
        Map<String, Object> companyName = field(
                "companyName", "公司名称", "TEXT", "companyName", "reported_company_name", true, false, 9
        );
        configureSurface(companyName, "adminForm", false, false, 9);
        configureSurface(companyName, "miniProgramForm", true, true, 2);
        configureValidation(companyName, Map.of("maxLength", 128));
        result.add(companyName);
        Map<String, Object> reportedAt = field(
                "reportedAt", "报修时间", "DATETIME", "reportedAt", "reported_at", false, true, 10
        );
        configureSurface(reportedAt, "miniProgramForm", true, false, 3);
        result.add(reportedAt);
        Map<String, Object> location = field(
                "location", "报修位置", "TEXT", "location", "location", false, false, 11
        );
        configureSurface(location, "miniProgramForm", false, false, 23);
        result.add(location);
        Map<String, Object> description = field(
                "description", "问题描述", "LONG_TEXT", "description", "description", true, false, 12
        );
        configureSurface(description, "miniProgramForm", true, true, 5);
        configureValidation(description, Map.of("maxLength", 2000));
        result.add(description);
        Map<String, Object> images = field(
                "images", "现场照片", "IMAGE", "images", "work_order_attachment", false, false, 13
        );
        configureSurface(images, "miniProgramForm", true, true, 7);
        configureValidation(images, Map.of("maxFiles", 6));
        result.add(images);
        Map<String, Object> attachments = field(
                "attachments", "附件", "ATTACHMENT", "attachments", "work_order_attachment", false, false, 14
        );
        configureSurface(attachments, "miniProgramForm", false, false, 24);
        result.add(attachments);
        Map<String, Object> remark = field(
                "remark", "备注", "LONG_TEXT", "remark", "remark", false, false, 15
        );
        configureSurface(remark, "miniProgramForm", true, true, 6);
        configureValidation(remark, Map.of("maxLength", 500));
        result.add(remark);
        result.add(field("createdTime", "创建时间", "DATETIME", "createdTime", "created_at", false, true, 16));
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> inspectionFields() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(field("id", "巡检编号", "NUMBER", "id", "id", false, true, 1));
        result.add(field("inspectionDate", "巡检日期", "DATE", "inspectionDate", "inspection_date", true, false, 2));
        Map<String, Object> inspector = field("inspector", "巡检人", "TEXT", "inspector", "inspector", true, false, 3);
        configureValidation(inspector, Map.of("maxLength", 64));
        inspector.put("description", "可自定义填写，最多64个字符；操作身份由系统记录");
        result.add(inspector);
        Map<String, Object> type = field("inspectionType", "巡检类型", "SELECT", "inspectionType", "inspection_type", true, false, 4);
        type.put("optionsSource", "/mobile/staff/inspection-types");
        type.put("description", "请选择启用的巡检类型");
        result.add(type);
        result.add(field("area", "区域/点位", "TEXT", "area", "area", true, false, 5));
        result.add(field("target", "巡检对象", "TEXT", "target", "target", true, false, 6));
        result.add(select("result", "巡检结果", "result", "result", true, 7, List.of(
                option("NORMAL", "正常"), option("ABNORMAL", "异常"), option("PENDING", "待处理")
        )));
        result.add(field("problemDescription", "问题描述", "LONG_TEXT", "problemDescription", "problem_description", false, false, 8));
        result.add(field("actionTaken", "处理措施", "LONG_TEXT", "actionTaken", "action_taken", false, false, 9));
        result.add(select("status", "巡检状态", "status", "inspection_status", false, 10, List.of(
                option("OPEN", "待处理"), option("CLOSED", "已关闭")
        ), true));
        result.add(field("images", "现场照片", "IMAGE", "images", "inspection_attachment", false, false, 11));
        result.add(field("remark", "备注", "LONG_TEXT", "remark", "remark", false, false, 12));
        result.add(field("createdAt", "创建时间", "DATETIME", "createdAt", "created_at", false, true, 13));
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> visitorFields() {
        List<Map<String, Object>> result = new ArrayList<>();
        result.add(field("visitorName", "访客姓名", "TEXT", "visitorName", "visitor_name", true, false, 1));
        result.add(field("visitorPhone", "访客手机号", "PHONE", "visitorPhone", "visitor_phone", true, false, 2));
        result.add(field("companyName", "来访单位", "TEXT", "companyName", "visit_company", false, false, 3));
        result.add(field("plateNumber", "车牌号", "TEXT", "plateNumber", "car_plate_no", false, false, 4));
        result.add(field("visitDate", "来访日期", "DATE", "visitDate", "visit_time", true, false, 5));
        result.add(field("visitTime", "来访时间", "TIME", "visitTime", "visit_time", true, false, 6));
        result.add(field("visitReason", "来访事由", "LONG_TEXT", "visitReason", "visit_reason", true, false, 7));
        result.add(field("remark", "备注", "LONG_TEXT", "remark", "remark", false, false, 8));
        result.add(select("status", "状态", "status", "appointment_status", false, 9, List.of(
                option("PENDING", "待确认"), option("REGISTERED", "待到访"),
                option("ENTERED", "已到访"), option("LEFT", "已离场"), option("CANCELLED", "已取消")
        ), true));
        result.add(field("createdAt", "创建时间", "DATETIME", "createdAt", "created_at", false, true, 10));
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> roomFields() {
        return List.of(
                field("roomCode", "房间编码", "TEXT", "roomCode", "code", false, true, 1),
                field("buildingId", "所属楼宇", "RELATION", "buildingId", "building_id", true, false, 2),
                field("floorId", "所属楼层", "RELATION", "floorId", "floor_id", true, false, 3),
                field("roomNumber", "房间号", "TEXT", "roomNumber", "room_no", true, false, 4),
                field("area", "建筑面积", "NUMBER", "area", "area", true, false, 5),
                field("propertyFeeArea", "物业费计费面积", "NUMBER", "propertyFeeArea", "property_fee_area", true, false, 6),
                select("roomType", "房间类型", "roomType", "usage_type", true, 7, List.of(
                        option("OFFICE", "办公"), option("SHOP", "商铺"), option("STORAGE", "仓储"), option("OTHER", "其他")
                )),
                field("status", "出租状态", "SELECT", "status", "rent_status", false, true, 8),
                field("description", "备注", "LONG_TEXT", "description", "remark", false, false, 9)
        );
    }

    private static List<Map<String, Object>> contractFields() {
        return List.of(
                field("contractNumber", "合同编号", "TEXT", "contractNumber", "code", false, true, 1),
                field("tenantName", "租户公司", "TEXT", "tenantName", "tenant.name", false, true, 2),
                field("rooms", "租赁房间", "LONG_TEXT", "rooms", "contract_room", false, true, 3),
                field("startDate", "开始日期", "DATE", "startDate", "start_date", true, true, 4),
                field("endDate", "结束日期", "DATE", "endDate", "end_date", true, true, 5),
                field("status", "合同状态", "SELECT", "status", "status", false, true, 6),
                field("rentAmount", "租金摘要", "CURRENCY", "rentAmount", "rent_amount", false, true, 7),
                field("depositAmount", "押金摘要", "CURRENCY", "depositAmount", "deposit_amount", false, true, 8),
                field("hasContractFile", "电子合同", "BOOLEAN", "hasContractFile", "contract_attachment", false, true, 9)
        );
    }

    private static List<Map<String, Object>> billFields() {
        return List.of(
                field("billNumber", "账单编号", "TEXT", "billNumber", "code", false, true, 1),
                field("billType", "账单类型", "SELECT", "billType", "bill_type", false, true, 2),
                field("periodStart", "计费开始", "DATE", "periodStart", "period_start", false, true, 3),
                field("periodEnd", "计费结束", "DATE", "periodEnd", "period_end", false, true, 4),
                field("amount", "应付金额", "CURRENCY", "amount", "amount", false, true, 5),
                field("paidAmount", "已付金额", "CURRENCY", "paidAmount", "paid_amount", false, true, 6),
                field("unpaidAmount", "未付金额", "CURRENCY", "unpaidAmount", "amount-paid_amount", false, true, 7),
                field("status", "账单状态", "SELECT", "status", "pay_status", false, true, 8),
                field("dueDate", "到期日", "DATE", "dueDate", "due_date", false, true, 9),
                field("overdueDays", "逾期天数", "NUMBER", "overdueDays", "computed", false, true, 10),
                field("sourceType", "费用来源", "SELECT", "sourceType", "source_type", false, true, 11),
                field("sourceDetail", "来源明细", "LONG_TEXT", "sourceDetail", "asset_charge_basis.snapshot_json", false, true, 12),
                field("invoiceStatus", "发票状态", "SELECT", "invoiceStatus", "bill_invoice.invoice_status", false, true, 13),
                field("createdAt", "创建时间", "DATETIME", "createdAt", "created_at", false, true, 14)
        );
    }

    private static List<Map<String, Object>> investmentContentFields() {
        return List.of(
                select("sectionKey", "展示区块", "sectionKey", "section_key", true, 1, List.of(
                        option("hero", "顶部主视觉"),
                        option("introduction", "园区介绍"),
                        option("highlight", "园区优势"),
                        option("policy", "招商政策"),
                        option("contact", "联系方式"),
                        option("location", "园区地址")
                )),
                field("title", "标题", "TEXT", "title", "title", true, false, 2),
                field("subtitle", "副标题", "TEXT", "subtitle", "subtitle", false, false, 3),
                field("content", "内容", "LONG_TEXT", "content", "content", true, false, 4),
                field("imageUrl", "展示图片", "IMAGE", "imageUrl", "image_url", false, false, 5),
                field("sortOrder", "展示排序", "NUMBER", "sortOrder", "sort_order", true, false, 6),
                field("effectiveAt", "生效时间", "DATETIME", "effectiveAt", "effective_at", false, false, 7),
                field("expiresAt", "失效时间", "DATETIME", "expiresAt", "expires_at", false, false, 8),
                field("publishStatus", "发布状态", "SELECT", "publishStatus", "publish_status", false, true, 9)
        );
    }

    private static List<Map<String, Object>> investmentListingFields() {
        return List.of(
                field("roomId", "真实房间", "RELATION", "roomId", "room_id", true, false, 1),
                field("buildingName", "楼宇", "TEXT", "buildingName", "building.name", false, true, 2),
                field("floorName", "楼层", "TEXT", "floorName", "building_floor.name", false, true, 3),
                field("roomNumber", "房号", "TEXT", "roomNumber", "building_room.room_no", false, true, 4),
                field("area", "展示面积", "NUMBER", "area", "building_room.area", false, true, 5),
                field("roomType", "房间类型", "SELECT", "roomType", "building_room.usage_type", false, true, 6),
                field("title", "展示标题", "TEXT", "title", "title", true, false, 7),
                field("subtitle", "副标题", "TEXT", "subtitle", "subtitle", false, false, 8),
                field("description", "营销描述", "LONG_TEXT", "description", "description", false, false, 9),
                field("tags", "标签", "LONG_TEXT", "tags", "tags", false, false, 10),
                field("coverImageUrl", "封面图", "IMAGE", "coverImageUrl", "cover_image_url", false, false, 11),
                field("recommended", "推荐", "CHECKBOX", "recommended", "is_recommended", false, false, 12),
                field("reservable", "允许预约", "CHECKBOX", "reservable", "is_reservable", false, false, 13),
                field("sortOrder", "展示排序", "NUMBER", "sortOrder", "sort_order", true, false, 14),
                field("effectiveAt", "生效时间", "DATETIME", "effectiveAt", "effective_at", false, false, 15),
                field("expiresAt", "失效时间", "DATETIME", "expiresAt", "expires_at", false, false, 16),
                field("publishStatus", "发布状态", "SELECT", "publishStatus", "publish_status", false, true, 17)
        );
    }

    private static List<Map<String, Object>> investmentLeadFields() {
        return List.of(
                field("name", "联系人姓名", "TEXT", "name", "name", true, false, 1),
                field("phone", "已验证手机号", "PHONE", "phone", "phone", true, true, 2),
                field("companyName", "公司名称", "TEXT", "companyName", "company_name", true, false, 3),
                field("listingId", "意向房源", "RELATION", "listingId", "listing_id", true, false, 4),
                field("desiredArea", "意向面积", "NUMBER", "desiredArea", "desired_area", false, false, 5),
                field("intendedUse", "用途", "TEXT", "intendedUse", "intended_use", false, false, 6),
                field("preferredVisitTime", "期望看房时间", "DATETIME", "preferredVisitTime", "preferred_visit_time", true, false, 7),
                field("remark", "备注", "LONG_TEXT", "remark", "remark", false, false, 8),
                field("source", "来源", "SELECT", "source", "source", false, true, 9),
                field("status", "状态", "SELECT", "status", "lead_status", false, true, 10)
        );
    }

    private static List<Map<String, Object>> announcementFields() {
        return List.of(
                field("code", "公告编号", "TEXT", "code", "code", false, true, 1),
                field("title", "标题", "TEXT", "title", "title", true, false, 2),
                field("summary", "摘要", "TEXT", "summary", "summary", false, false, 3),
                field("content", "内容", "LONG_TEXT", "content", "content", true, false, 4),
                field("noticeType", "公告类型", "SELECT", "noticeType", "notice_type", true, false, 5),
                field("coverImageUrl", "封面图", "IMAGE", "coverImageUrl", "cover_image_url", false, false, 6),
                field("showOnHome", "首页轮播", "CHECKBOX", "showOnHome", "show_on_home", false, false, 7),
                field("carouselSort", "轮播排序", "NUMBER", "carouselSort", "carousel_sort", false, false, 8),
                field("effectiveAt", "生效时间", "DATETIME", "effectiveAt", "effective_at", false, false, 9),
                field("expiresAt", "失效时间", "DATETIME", "expiresAt", "expires_at", false, false, 10),
                field("publishStatus", "发布状态", "SELECT", "publishStatus", "publish_status", false, true, 11)
        );
    }

    private static List<Map<String, Object>> equipmentFields() {
        return List.of(
                field("equipmentCode", "设备编号", "TEXT", "equipmentCode", "equipment_code", true, false, 1),
                field("equipmentName", "设备名称", "TEXT", "equipmentName", "equipment_name", true, false, 2),
                field("equipmentType", "设备分类", "SELECT", "equipmentType", "equipment_type", true, false, 3),
                field("manufacturer", "品牌/厂家", "TEXT", "manufacturer", "manufacturer", false, false, 4),
                field("model", "型号", "TEXT", "model", "model", false, false, 5),
                field("serialNo", "序列号", "TEXT", "serialNo", "serial_no", false, false, 6),
                field("buildingId", "所属楼宇", "RELATION", "buildingId", "building_id", false, false, 7),
                field("floorId", "所属楼层", "RELATION", "floorId", "floor_id", false, false, 8),
                field("roomId", "所属房间", "RELATION", "roomId", "room_id", false, false, 9),
                field("location", "位置说明", "TEXT", "location", "location", false, false, 10),
                field("installDate", "启用日期", "DATE", "installDate", "install_date", false, false, 11),
                field("responsibleDepartment", "责任部门", "TEXT", "responsibleDepartment", "responsible_department", false, false, 12),
                field("responsibleUserId", "责任人", "RELATION", "responsibleUserId", "responsible_user_id", false, false, 13),
                field("serviceLifeYears", "使用年限", "NUMBER", "serviceLifeYears", "service_life_years", false, false, 14),
                field("imageFileIds", "设备图片", "IMAGE", "imageFileIds", "image_file_ids", false, false, 15),
                field("attachmentFileIds", "设备附件", "ATTACHMENT", "attachmentFileIds", "attachment_file_ids", false, false, 16),
                field("status", "设备状态", "SELECT", "status", "status", true, false, 17),
                field("remark", "备注", "LONG_TEXT", "remark", "remark", false, false, 18)
        );
    }

    private static List<Map<String, Object>> energyMeterFields() {
        return List.of(
                field("meterNo", "表具编号", "TEXT", "meterNo", "meter_no", true, false, 1),
                field("meterName", "表具名称", "TEXT", "meterName", "meter_name", true, false, 2),
                select("meterType", "表具类型", "meterType", "meter_type", true, 3, List.of(
                        option("WATER", "水"), option("ELECTRIC", "电"), option("GAS", "燃气")
                )),
                field("buildingId", "所属楼宇", "RELATION", "buildingId", "building_id", false, false, 4),
                field("floorId", "所属楼层", "RELATION", "floorId", "floor_id", false, false, 5),
                field("roomId", "所属房间", "RELATION", "roomId", "room_id", false, false, 6),
                field("locationDescription", "安装位置", "TEXT", "locationDescription", "location_description", false, false, 7),
                field("initialReading", "初始读数", "NUMBER", "initialReading", "initial_reading", true, false, 8),
                field("multiplier", "倍率", "NUMBER", "multiplier", "multiplier", true, false, 9),
                field("commissioningDate", "启用日期", "DATE", "commissioningDate", "commissioning_date", false, false, 10),
                field("status", "状态", "SELECT", "status", "status", true, false, 11)
        );
    }

    private static List<Map<String, Object>> energyReadingFields() {
        return List.of(
                field("meterId", "表具", "RELATION", "meterId", "meter_id", true, false, 1),
                field("periodMonth", "账期", "MONTH", "periodMonth", "period_month", true, false, 2),
                field("previousReading", "上期读数", "NUMBER", "previousReading", "previous_reading", false, true, 3),
                field("currentReading", "本期读数", "NUMBER", "currentReading", "current_reading", true, false, 4),
                field("usageAmount", "用量", "NUMBER", "usageAmount", "usage_amount", false, true, 5),
                field("readingDate", "抄表日期", "DATE", "readingDate", "reading_date", true, false, 6),
                field("source", "来源", "SELECT", "source", "reading_source", false, true, 7),
                field("attachmentFileId", "抄表图片", "IMAGE", "attachmentFileIds", "attachment_file_id", false, false, 8),
                field("resetReason", "回退/归零原因", "LONG_TEXT", "resetReason", "reset_reason", false, false, 9),
                field("unitPrice", "单价快照", "NUMBER", "unitPrice", "unit_price", false, true, 10),
                field("settlementAmount", "费用依据金额", "NUMBER", "settlementAmount", "settlement_amount", false, true, 11),
                field("billStatus", "费用依据状态", "SELECT", "billStatus", "bill_status", false, true, 12)
        );
    }

    private static List<Map<String, Object>> parkingSpaceFields() {
        return List.of(
                field("spaceNo", "车位编号", "TEXT", "spaceNo", "space_no", true, false, 1),
                field("area", "区域", "TEXT", "area", "area", true, false, 2),
                field("floor", "楼层", "TEXT", "floor", "location", true, false, 3),
                field("spaceType", "车位类型", "SELECT", "spaceType", "space_type", true, false, 4),
                field("monthlyRate", "月租标准", "NUMBER", "monthlyRate", "monthly_rate", false, false, 5),
                field("status", "占用状态", "SELECT", "status", "rent_status", false, true, 6),
                field("remark", "备注", "LONG_TEXT", "remark", "remark", false, false, 7)
        );
    }

    private static List<Map<String, Object>> parkingAssignmentFields() {
        return List.of(
                field("parkingSpaceId", "车位", "RELATION", "parkingSpaceId", "parking_space_id", true, false, 1),
                field("tenantId", "租户", "RELATION", "tenantId", "tenant_id", true, false, 2),
                field("tenantContactId", "联系人", "RELATION", "tenantContactId", "tenant_contact_id", false, false, 3),
                field("plateNo", "车牌号", "TEXT", "plateNo", "car_plate_no", false, false, 4),
                field("vehicleType", "车辆类型", "SELECT", "vehicleType", "vehicle_type", false, false, 5),
                field("startDate", "生效日期", "DATE", "startDate", "start_date", true, false, 6),
                field("endDate", "失效日期", "DATE", "endDate", "end_date", false, false, 7),
                field("unitPriceSnapshot", "收费标准快照", "NUMBER", "unitPriceSnapshot", "unit_price_snapshot", true, true, 8),
                field("status", "状态", "SELECT", "status", "status", false, true, 9)
        );
    }

    private static List<Map<String, Object>> meetingRoomFields() {
        return List.of(
                field("code", "会议室编号", "TEXT", "code", "code", true, false, 1),
                field("name", "会议室名称", "TEXT", "name", "name", true, false, 2),
                field("location", "位置", "TEXT", "location", "location", false, false, 3),
                field("capacity", "容纳人数", "NUMBER", "capacity", "capacity", true, false, 4),
                field("facilities", "设备设施", "LONG_TEXT", "facilities", "facilities", false, false, 5),
                field("description", "房间描述", "LONG_TEXT", "description", "description", false, false, 6),
                field("imageFileIds", "会议室图片", "IMAGE", "imageFileIds", "image_file_ids", false, false, 7),
                field("openTime", "开放开始", "TIME", "openTime", "open_time", false, false, 8),
                field("closeTime", "开放结束", "TIME", "closeTime", "close_time", false, false, 9),
                field("status", "启用状态", "SELECT", "status", "status", true, false, 10)
        );
    }

    private static List<Map<String, Object>> meetingBookingFields() {
        return List.of(
                field("meetingRoomId", "会议室", "RELATION", "meetingRoomId", "meeting_room_id", true, false, 1),
                field("startTime", "开始时间", "DATETIME", "startTime", "start_time", true, false, 2),
                field("endTime", "结束时间", "DATETIME", "endTime", "end_time", true, false, 3),
                field("purpose", "会议用途", "LONG_TEXT", "purpose", "purpose", true, false, 4),
                field("participantCount", "参会人数", "NUMBER", "participantCount", "participant_count", true, false, 5),
                field("departmentName", "部门", "TEXT", "departmentName", "department_name", false, false, 6),
                field("contactName", "联系人", "TEXT", "contactName", "contact_name", false, true, 7),
                field("contactPhone", "联系电话", "PHONE", "contactPhone", "contact_phone", false, true, 8),
                field("unitPriceSnapshot", "单价快照", "NUMBER", "unitPriceSnapshot", "unit_price_snapshot", false, true, 9),
                field("amount", "预约费用", "NUMBER", "amount", "amount", false, true, 10),
                field("status", "预约状态", "SELECT", "status", "booking_status", false, true, 11),
                field("remark", "备注", "LONG_TEXT", "remark", "remark", false, false, 12)
        );
    }

    private static LinkedHashMap<String, Object> field(
            String fieldKey,
            String label,
            String dataType,
            String apiField,
            String storageField,
            boolean required,
            boolean readonly,
            int order
    ) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("fieldKey", fieldKey);
        result.put("label", label);
        result.put("source", "SYSTEM");
        result.put("dataType", dataType);
        result.put("apiField", apiField);
        result.put("storageField", storageField);
        result.put("required", required);
        result.put("readonly", readonly);
        result.put("sortable", !Set.of("LONG_TEXT", "IMAGE", "ATTACHMENT").contains(dataType));
        result.put("filterable", !Set.of("LONG_TEXT", "IMAGE", "ATTACHMENT").contains(dataType));
        result.put("options", List.of());
        result.put("validation", validation(dataType));
        result.put("surfaces", surfaces(order, readonly));
        return result;
    }

    private static Map<String, Object> select(
            String fieldKey,
            String label,
            String apiField,
            String storageField,
            boolean required,
            int order,
            List<Map<String, String>> options
    ) {
        return select(fieldKey, label, apiField, storageField, required, order, options, false);
    }

    private static Map<String, Object> select(
            String fieldKey,
            String label,
            String apiField,
            String storageField,
            boolean required,
            int order,
            List<Map<String, String>> options,
            boolean readonly
    ) {
        LinkedHashMap<String, Object> result = field(
                fieldKey, label, "SELECT", apiField, storageField, required, readonly, order
        );
        result.put("options", options);
        return result;
    }

    private static Map<String, Object> validation(String dataType) {
        if ("TEXT".equals(dataType) || "PHONE".equals(dataType)) return Map.of("maxLength", 200);
        if ("LONG_TEXT".equals(dataType)) return Map.of("maxLength", 10_000);
        if ("IMAGE".equals(dataType) || "ATTACHMENT".equals(dataType)) return Map.of("maxFiles", 30);
        return Map.of();
    }

    private static Map<String, Object> surfaces(int order, boolean readonly) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("adminList", surface(true, false, order));
        result.put("adminForm", surface(true, !readonly, order));
        result.put("adminDetail", surface(true, false, order));
        result.put("miniProgramForm", surface(!readonly, !readonly, order));
        result.put("miniProgramDetail", surface(true, false, order));
        return result;
    }

    private static Map<String, Object> surface(boolean visible, boolean editable, int order) {
        return Map.of("visible", visible, "editable", editable, "order", order);
    }

    private static void configureSurface(
            Map<String, Object> field,
            String surfaceKey,
            boolean visible,
            boolean editable,
            int order
    ) {
        LinkedHashMap<String, Object> configured = new LinkedHashMap<>();
        Object source = field.get("surfaces");
        if (source instanceof Map<?, ?> sourceMap) {
            sourceMap.forEach((key, value) -> configured.put(String.valueOf(key), value));
        }
        configured.put(surfaceKey, surface(visible, editable, order));
        field.put("surfaces", configured);
    }

    private static void configureValidation(Map<String, Object> field, Map<String, Object> overrides) {
        LinkedHashMap<String, Object> configured = new LinkedHashMap<>();
        Object source = field.get("validation");
        if (source instanceof Map<?, ?> sourceMap) {
            sourceMap.forEach((key, value) -> configured.put(String.valueOf(key), value));
        }
        configured.putAll(overrides);
        field.put("validation", configured);
    }

    private static Map<String, String> option(String code, String label) {
        return Map.of("code", code, "label", label);
    }
}
