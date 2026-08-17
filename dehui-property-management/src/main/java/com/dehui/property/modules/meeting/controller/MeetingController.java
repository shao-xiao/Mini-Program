package com.dehui.property.modules.meeting.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.ContactValidators;
import com.dehui.property.common.JdbcPagination;
import com.dehui.property.common.JdbcMaps;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.asset.application.AssetChargeBasisService;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.ModuleFieldSubmissionValidator;
import com.dehui.property.modules.meeting.application.MeetingRoomImageService;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.dehui.property.modules.mobile.service.MobileCustomFieldValueService;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.AuthInterceptor;
import com.dehui.property.security.AuthPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeetingController {

    private final JdbcTemplate jdbcTemplate;
    private final AssetChargeBasisService assetChargeBasisService;
    private final CustomFieldProjectionService customFieldProjectionService;
    private final MobileCustomFieldValueService mobileCustomFieldValueService;
    private final MobileIdentityService mobileIdentityService;
    private final MeetingRoomImageService meetingRoomImageService;
    private ModuleFieldSubmissionValidator moduleFieldSubmissionValidator;

    public MeetingController(JdbcTemplate jdbcTemplate) {
        this(
                jdbcTemplate,
                new AssetChargeBasisService(jdbcTemplate, new ObjectMapper()),
                null,
                null,
                new MobileIdentityService(jdbcTemplate)
        );
    }

    public MeetingController(
            JdbcTemplate jdbcTemplate,
            AssetChargeBasisService assetChargeBasisService) {
        this(jdbcTemplate, assetChargeBasisService, null, null, new MobileIdentityService(jdbcTemplate));
    }

    public MeetingController(
            JdbcTemplate jdbcTemplate,
            AssetChargeBasisService assetChargeBasisService,
            CustomFieldProjectionService customFieldProjectionService,
            MobileCustomFieldValueService mobileCustomFieldValueService) {
        this(
                jdbcTemplate,
                assetChargeBasisService,
                customFieldProjectionService,
                mobileCustomFieldValueService,
                new MobileIdentityService(jdbcTemplate)
        );
    }

    public MeetingController(
            JdbcTemplate jdbcTemplate,
            AssetChargeBasisService assetChargeBasisService,
            CustomFieldProjectionService customFieldProjectionService,
            MobileCustomFieldValueService mobileCustomFieldValueService,
            MobileIdentityService mobileIdentityService) {
        this(
                jdbcTemplate,
                assetChargeBasisService,
                customFieldProjectionService,
                mobileCustomFieldValueService,
                mobileIdentityService,
                new MeetingRoomImageService(jdbcTemplate, new ObjectMapper(), null)
        );
    }

    @Autowired
    public MeetingController(
            JdbcTemplate jdbcTemplate,
            AssetChargeBasisService assetChargeBasisService,
            CustomFieldProjectionService customFieldProjectionService,
            MobileCustomFieldValueService mobileCustomFieldValueService,
            MobileIdentityService mobileIdentityService,
            MeetingRoomImageService meetingRoomImageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.assetChargeBasisService = assetChargeBasisService;
        this.customFieldProjectionService = customFieldProjectionService;
        this.mobileCustomFieldValueService = mobileCustomFieldValueService;
        this.mobileIdentityService = mobileIdentityService;
        this.meetingRoomImageService = meetingRoomImageService;
    }

    @Autowired(required = false)
    void setModuleFieldSubmissionValidator(ModuleFieldSubmissionValidator service) {
        this.moduleFieldSubmissionValidator = service;
    }

    @GetMapping("/mobile/public/meeting-rooms")
    public ApiResponse<PageResponse<Map<String, Object>>> publicMeetingRooms(
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        String start = normalizeDateTime(startTime, LocalDateTime.now().toString());
        String end = normalizeDateTime(endTime, start);
        PageResponse<Map<String, Object>> result = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT room_record.id,
                       room_record.id AS roomId,
                       room_record.name,
                       room_record.name AS roomName,
                       room_record.location,
                       room_record.capacity,
                       room_record.facilities,
                       room_record.description,
                       room_record.open_time AS openTime,
                       room_record.close_time AS closeTime,
                       room_record.image_file_ids AS imageFileIds,
                       CASE WHEN NOT EXISTS (
                         SELECT 1
                         FROM meeting_booking booking_record
                         WHERE booking_record.meeting_room_id = room_record.id
                           AND booking_record.deleted = 0
                           AND booking_record.booking_status NOT IN ('CANCELLED', 'REJECTED')
                           AND booking_record.start_time < ?
                           AND booking_record.end_time > ?
                       ) THEN 1 ELSE 0 END AS available
                FROM meeting_room room_record
                WHERE room_record.deleted = 0
                  AND room_record.status IN ('ACTIVE', 'ENABLED', 'AVAILABLE')
                ORDER BY room_record.id DESC
                """,
                """
                SELECT COUNT(*)
                FROM meeting_room room_record
                WHERE room_record.deleted = 0
                  AND room_record.status IN ('ACTIVE', 'ENABLED', 'AVAILABLE')
                """,
                List.of(),
                List.of(end, start),
                page,
                pageSize
        );
        result = new PageResponse<>(
                meetingRoomImageService.decoratePublicRooms(result.records()),
                result.total(),
                result.page(),
                result.pageSize()
        );
        return ApiResponse.success(result);
    }

    @GetMapping("/mobile/public/meeting-rooms/{roomId}/availability")
    public ApiResponse<Map<String, Object>> publicMeetingAvailability(
            @PathVariable Long roomId,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        LocalDateTime start = parseDateTime(startTime);
        LocalDateTime end = parseDateTime(endTime);
        if (!end.isAfter(start)) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        Long roomCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM meeting_room WHERE id = ? AND deleted = 0 AND status IN ('ACTIVE','ENABLED','AVAILABLE')",
                Long.class,
                roomId
        );
        if (roomCount == null || roomCount == 0) {
            throw new BusinessException(404, "会议室不存在或未开放");
        }
        Long conflicts = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM meeting_booking
                WHERE meeting_room_id = ?
                  AND deleted = 0
                  AND booking_status NOT IN ('CANCELLED', 'REJECTED')
                  AND start_time < ?
                  AND end_time > ?
                """,
                Long.class,
                roomId,
                end,
                start
        );
        return ApiResponse.success(Map.of("available", conflicts == null || conflicts == 0));
    }

    @GetMapping({"/meeting-rooms", "/mobile/meeting-rooms", "/meeting-rooms/available"})
    public ApiResponse<List<Map<String, Object>>> meetingRooms() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT room_record.id,
                       room_record.code,
                       room_record.name,
                       room_record.capacity,
                       room_record.location,
                       room_record.facilities,
                       room_record.description,
                       room_record.open_time AS openTime,
                       room_record.close_time AS closeTime,
                       room_record.image_file_ids AS imageFileIds,
                       COALESCE(rule_record.hourly_price, room_record.hourly_price) AS workdayHourlyRate,
                       COALESCE(rule_record.charge_type, CASE WHEN room_record.hourly_price > 0 THEN 'HOURLY' ELSE 'FREE' END) AS chargeType,
                       rule_record.minimum_minutes AS minimumMinutes,
                       room_record.status,
                       room_record.remark
                FROM meeting_room room_record
                LEFT JOIN meeting_rate_rule rule_record ON rule_record.id = (
                    SELECT active_rule.id
                    FROM meeting_rate_rule active_rule
                    WHERE active_rule.meeting_room_id = room_record.id
                      AND active_rule.deleted = 0
                      AND active_rule.status = 'ACTIVE'
                      AND active_rule.effective_from <= CURRENT_DATE
                      AND (active_rule.effective_to IS NULL OR active_rule.effective_to >= CURRENT_DATE)
                    ORDER BY active_rule.effective_from DESC, active_rule.id DESC
                    LIMIT 1
                )
                WHERE room_record.deleted = 0
                ORDER BY room_record.id DESC
                """
        ));
    }

    @PostMapping("/meeting-rooms")
    public ApiResponse<Void> createMeetingRoom(@RequestBody Map<String, Object> body) {
        saveMeetingRoom(null, body);
        return ApiResponse.success();
    }

    @PutMapping("/meeting-rooms/{id}")
    public ApiResponse<Void> updateMeetingRoom(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        saveMeetingRoom(id, body);
        return ApiResponse.success();
    }

    @DeleteMapping("/meeting-rooms/{id}")
    public ApiResponse<Void> deleteMeetingRoom(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE meeting_room SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Meeting room not found");
        return ApiResponse.success();
    }

    @GetMapping("/meeting-rooms/{roomId}/rate-rules")
    public ApiResponse<List<Map<String, Object>>> rateRules(@PathVariable Long roomId) {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, meeting_room_id AS meetingRoomId,
                       charge_type AS chargeType, hourly_price AS hourlyPrice,
                       minimum_minutes AS minimumMinutes,
                       effective_from AS effectiveFrom, effective_to AS effectiveTo,
                       status, remark
                FROM meeting_rate_rule
                WHERE meeting_room_id = ? AND deleted = 0
                ORDER BY effective_from DESC, id DESC
                """,
                roomId
        ));
    }

    @PostMapping("/meeting-rooms/{roomId}/rate-rules")
    @Transactional
    public ApiResponse<Map<String, Object>> createRateRule(
            @PathVariable Long roomId,
            @RequestBody Map<String, Object> body) {
        lockMeetingRoom(roomId);
        RateRuleInput input = rateRuleInput(body);
        ensureRateRuleNoOverlap(roomId, input.effectiveFrom(), input.effectiveTo());
        Long id = insertRateRule(roomId, input);
        return ApiResponse.success(Map.of("id", id));
    }

    @PostMapping("/meeting-rooms/{roomId}/rate-rules/replace")
    @Transactional
    public ApiResponse<Map<String, Object>> replaceRateRule(
            @PathVariable Long roomId,
            @RequestBody Map<String, Object> body) {
        lockMeetingRoom(roomId);
        RateRuleInput input = rateRuleInput(body);
        jdbcTemplate.update(
                """
                UPDATE meeting_rate_rule
                SET effective_to = ?,
                    status = 'RETIRED',
                    updated_by = ?
                WHERE meeting_room_id = ?
                  AND deleted = 0
                  AND status = 'ACTIVE'
                  AND effective_from < ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                """,
                input.effectiveFrom().minusDays(1),
                0L,
                roomId,
                input.effectiveFrom(),
                input.effectiveFrom()
        );
        ensureRateRuleNoOverlap(roomId, input.effectiveFrom(), input.effectiveTo());
        Long id = insertRateRule(roomId, input);
        return ApiResponse.success(Map.of("id", id));
    }

    @GetMapping("/meeting-bookings")
    public ApiResponse<List<Map<String, Object>>> meetingBookings() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT b.id, b.code AS bookingNo, b.meeting_room_id AS roomId, r.name AS roomName,
                       b.tenant_id AS tenantId, b.start_time AS startTime, b.end_time AS endTime,
                       b.booking_status AS status, b.purpose, b.participant_count AS participantCount,
                       b.contact_name AS contactName, b.contact_phone AS contactPhone,
                       b.department_name AS departmentName,
                       b.review_reply AS reviewReply,
                       COALESCE(NULLIF(reviewer.real_name, ''), reviewer.username) AS reviewerName,
                       b.reviewed_at AS reviewedAt,
                       b.pricing_rule_id AS pricingRuleId,
                       b.unit_price_snapshot AS unitPriceSnapshot,
                       b.duration_minutes AS durationMinutes,
                       b.amount,
                       b.charge_basis_id AS chargeBasisId,
                       b.remark,
                       CASE WHEN b.tenant_id IS NOT NULL THEN 'TENANT'
                            WHEN b.applicant_mobile_user_id IS NOT NULL THEN 'PUBLIC'
                            ELSE 'INTERNAL' END AS sourceType,
                       t.name AS tenantName,
                       COALESCE(NULLIF(b.contact_name, ''), mu.nickname, t.name, '内部预约') AS applicantName,
                       basis_record.bill_id AS billingId,
                       gb.code AS billNumber,
                       CASE WHEN gb.pay_status = 'PAID' THEN 1 ELSE 0 END AS billPaid,
                       CASE WHEN b.tenant_id IS NULL AND b.applicant_mobile_user_id IS NULL THEN 'INTERNAL_FREE'
                            WHEN b.amount > 0 THEN 'HOURLY'
                            ELSE 'FREE' END AS feeType,
                       b.created_at AS createdAt, b.updated_at AS updatedAt
                FROM meeting_booking b
                LEFT JOIN meeting_room r ON r.id = b.meeting_room_id
                LEFT JOIN tenant t ON t.id = b.tenant_id
                LEFT JOIN mobile_user mu ON mu.id = b.applicant_mobile_user_id AND mu.deleted = 0
                LEFT JOIN sys_user reviewer ON reviewer.id = b.reviewed_by AND reviewer.deleted = 0
                LEFT JOIN asset_charge_basis basis_record ON basis_record.id = b.charge_basis_id AND basis_record.deleted = 0
                LEFT JOIN bill gb ON gb.id = basis_record.bill_id AND gb.deleted = 0
                WHERE b.deleted = 0
                ORDER BY b.created_at DESC, b.id DESC
                """
        ));
    }

    @GetMapping("/mobile/meetings")
    public ApiResponse<Map<String, Object>> mobileMeetings(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        boolean staffScope = principal != null && "STAFF".equals(principal.userType());
        if (staffScope) {
            MobileApiSupport.requireCapability(principal, "meeting.staff.view");
            MobileApiSupport.requireStaffUserId(jdbcTemplate, principal);
        } else {
            MobileApiSupport.requireCapability(principal, "meeting.book");
        }
        Long tenantId = staffScope ? null : MobileApiSupport.requireTenantId(principal);
        boolean companyScope = !staffScope && MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        String start = normalizeDateTime(startTime, JdbcMaps.now());
        String end = normalizeDateTime(endTime, start);
        List<Map<String, Object>> rooms = jdbcTemplate.queryForList(
                """
                SELECT r.id,
                       r.id AS roomId,
                       r.name AS roomName,
                       r.name,
                       r.capacity,
                       r.location,
                       r.facilities,
                       r.description,
                       r.open_time AS openTime,
                       r.close_time AS closeTime,
                       r.image_file_ids AS imageFileIds,
                       COALESCE(active_rule.hourly_price, r.hourly_price) AS workdayWorkHourRate,
                       COALESCE(active_rule.charge_type, CASE WHEN r.hourly_price > 0 THEN 'HOURLY' ELSE 'FREE' END) AS chargeType,
                       active_rule.minimum_minutes AS minimumMinutes,
                       CASE WHEN r.status IN ('ENABLED', 'AVAILABLE') AND NOT EXISTS (
                           SELECT 1
                           FROM meeting_booking b
                           WHERE b.meeting_room_id = r.id
                             AND b.deleted = 0
                             AND b.booking_status NOT IN ('CANCELLED', 'REJECTED')
                             AND b.start_time < ?
                             AND b.end_time > ?
                       ) THEN 1 ELSE 0 END AS available
                FROM meeting_room r
                LEFT JOIN meeting_rate_rule active_rule ON active_rule.id = (
                    SELECT rule_record.id
                    FROM meeting_rate_rule rule_record
                    WHERE rule_record.meeting_room_id = r.id
                      AND rule_record.deleted = 0
                      AND rule_record.status = 'ACTIVE'
                      AND rule_record.effective_from <= CURRENT_DATE
                      AND (rule_record.effective_to IS NULL OR rule_record.effective_to >= CURRENT_DATE)
                    ORDER BY rule_record.effective_from DESC, rule_record.id DESC
                    LIMIT 1
                )
                WHERE r.deleted = 0
                  AND r.status IN ('ACTIVE', 'ENABLED', 'AVAILABLE')
                ORDER BY r.id DESC
                """,
                end,
                start
        ).stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            return copy;
        }).toList();
        rooms.forEach(room -> {
            boolean available = isTruthy(room.get("available"));
            room.put("available", available);
            if (!available) {
                room.put("unavailableReason", "当前时间段不可预约");
            }
        });
        rooms = meetingRoomImageService.decorateRooms(rooms);
        String statusFilter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        List<Object> bookingArgs = new java.util.ArrayList<>();
        String bookingScope;
        if (staffScope) {
            bookingScope = "";
        } else {
            bookingScope = " AND b.tenant_id = ? AND (? = 1 OR b.applicant_mobile_user_id = ?)";
            bookingArgs.add(tenantId);
            bookingArgs.add(companyScope ? 1 : 0);
            bookingArgs.add(principal.userId());
        }
        if (statusFilter != null) {
            bookingScope += " AND b.booking_status = ?";
            bookingArgs.add(statusFilter);
        }
        PageResponse<Map<String, Object>> bookingPage = JdbcPagination.query(
                jdbcTemplate,
                """
                SELECT b.id,
                       b.code AS bookingNumber,
                       b.meeting_room_id AS meetingRoomId,
                       r.name AS meetingRoomName,
                       t.name AS tenantName,
                       b.start_time AS startTime,
                       b.end_time AS endTime,
                       b.booking_status AS status,
                       b.purpose,
                       b.participant_count AS participantCount,
                       b.contact_name AS contactName,
                       b.contact_phone AS contactPhone,
                       b.department_name AS departmentName,
                       b.review_reply AS reviewReply,
                       b.reviewed_at AS reviewedAt,
                       b.remark,
                       b.amount AS calculatedAmount,
                       b.amount,
                       b.created_at AS createdAt
                FROM meeting_booking b
                LEFT JOIN meeting_room r ON r.id = b.meeting_room_id
                LEFT JOIN tenant t ON t.id = b.tenant_id AND t.deleted = 0
                WHERE b.deleted = 0
                """ + bookingScope + "\n" + """
                ORDER BY b.start_time DESC, b.id DESC
                """,
                "SELECT COUNT(*) FROM meeting_booking b WHERE b.deleted = 0" + bookingScope,
                bookingArgs,
                page,
                pageSize
        );
        List<Map<String, Object>> bookings = bookingPage.records();
        if (customFieldProjectionService != null) {
            bookings = customFieldProjectionService.attach("meeting-bookings", bookings);
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("profile", MobileApiSupport.profile(jdbcTemplate, principal));
        response.put("rooms", rooms);
        response.put("bookings", bookings);
        response.put("records", bookings);
        response.put("total", bookingPage.total());
        response.put("page", bookingPage.page());
        response.put("pageSize", bookingPage.pageSize());
        return ApiResponse.success(response);
    }

    public ApiResponse<Map<String, Object>> mobileMeetings(
            AuthPrincipal principal,
            String startTime,
            String endTime) {
        return mobileMeetings(principal, startTime, endTime, null, null, null);
    }

    @GetMapping("/mobile/meetings/bookings/{id}")
    public ApiResponse<Map<String, Object>> mobileBookingDetail(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        MobileApiSupport.requireCapability(principal, "meeting.book");
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT b.id,
                       b.code AS bookingNumber,
                       b.meeting_room_id AS meetingRoomId,
                       r.name AS meetingRoomName,
                       r.location,
                       b.start_time AS startTime,
                       b.end_time AS endTime,
                       b.booking_status AS status,
                       b.unit_price_snapshot AS unitPriceSnapshot,
                       b.amount AS calculatedAmount,
                       b.purpose,
                       b.participant_count AS participantCount,
                       b.contact_name AS contactName,
                       b.contact_phone AS contactPhone,
                       b.department_name AS departmentName,
                       b.review_reply AS reviewReply,
                       b.reviewed_at AS reviewedAt,
                       b.remark,
                       b.created_at AS createdAt
                FROM meeting_booking b
                LEFT JOIN meeting_room r ON r.id = b.meeting_room_id
                WHERE b.id = ?
                  AND b.tenant_id = ?
                  AND (? = 1 OR b.applicant_mobile_user_id = ?)
                  AND b.deleted = 0
                """,
                id,
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "预约记录不存在或无权访问");
        }
        List<Map<String, Object>> decorated = customFieldProjectionService == null
                ? rows
                : customFieldProjectionService.attach("meeting-bookings", rows);
        return ApiResponse.success(decorated.getFirst());
    }

    @GetMapping("/meeting-bookings/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(jdbcTemplate.queryForMap(
                """
                SELECT COUNT(CASE WHEN DATE(start_time) = CURRENT_DATE THEN 1 END) AS todayBookingCount,
                       COUNT(CASE WHEN DATE_FORMAT(start_time, '%Y-%m') = DATE_FORMAT(CURRENT_DATE, '%Y-%m') THEN 1 END) AS monthBookingCount,
                       COALESCE(SUM(CASE
                           WHEN DATE_FORMAT(start_time, '%Y-%m') = DATE_FORMAT(CURRENT_DATE, '%Y-%m')
                            AND booking_status NOT IN ('CANCELLED', 'REJECTED')
                           THEN amount ELSE 0 END), 0) AS monthRevenue,
                       CASE WHEN COUNT(*) = 0 THEN 0
                            ELSE ROUND(SUM(CASE WHEN booking_status = 'CANCELLED' THEN 1 ELSE 0 END) / COUNT(*), 4)
                       END AS cancelRate
                FROM meeting_booking
                WHERE deleted = 0
                """
        ));
    }

    @GetMapping("/meeting-bookings/calculate")
    public ApiResponse<Map<String, Object>> calculate(
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        if ("INTERNAL".equalsIgnoreCase(sourceType)) {
            return ApiResponse.success(Map.of("feeType", "INTERNAL_FREE", "amount", BigDecimal.ZERO));
        }
        if (roomId == null || startTime == null || endTime == null) {
            return ApiResponse.success(Map.of("feeType", "HOURLY", "amount", BigDecimal.ZERO));
        }
        return ApiResponse.success(Map.of("feeType", "HOURLY", "amount", calculateAmount(roomId, startTime, endTime)));
    }

    @PostMapping("/meeting-bookings")
    @Transactional
    public ApiResponse<Void> createBooking(@RequestBody Map<String, Object> body) {
        createBookingRecord(
                JdbcMaps.requiredLong(body, "Meeting room is required", "roomId", "meetingRoomId"),
                JdbcMaps.longVal(body, "tenantId"),
                null,
                body,
                "PENDING",
                0L
        );
        return ApiResponse.success();
    }

    @PostMapping("/mobile/public/meeting-bookings")
    @Transactional
    public ApiResponse<Map<String, Object>> publicCreateBooking(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        if (principal == null || !"PUBLIC".equals(MobileApiSupport.normalizeUserType(principal.userType()))) {
            throw BusinessException.forbidden("该入口仅用于已验证手机号的游客预约");
        }
        MobileApiSupport.requireCapability(principal, "meeting.guest.book");
        String bindingState = mobileIdentityService.bindingState(principal.userId(), "PUBLIC");
        if (!"UNMATCHED".equals(bindingState)) {
            if ("UNVERIFIED".equals(bindingState) || "REVERIFY_REQUIRED".equals(bindingState)) {
                throw new BusinessException(409, "请先完成微信手机号快捷登录后再预约会议室");
            }
            throw new BusinessException(409, "该手机号已匹配业务身份，请先选择租户或物业身份");
        }
        if (moduleFieldSubmissionValidator != null) {
            moduleFieldSubmissionValidator.validateMobileCreate(
                    "meeting-bookings",
                    body,
                    principal,
                    Set.of("contactName", "contactPhone")
            );
        }
        Map<String, Object> contact = publicBookingContact(principal.userId());
        Map<String, Object> trustedBody = new LinkedHashMap<>(body);
        trustedBody.remove("sourceType");
        trustedBody.remove("tenantId");
        trustedBody.remove("tenantContactId");
        trustedBody.remove("sysUserId");
        trustedBody.remove("mobileUserId");
        trustedBody.remove("applicantMobileUserId");
        trustedBody.remove("operatorId");
        if (JdbcMaps.str(trustedBody, "contactName") == null) {
            trustedBody.put("contactName", contact.get("contactName"));
        }
        String submittedContactPhone = ContactValidators.optionalPhone(
                JdbcMaps.str(trustedBody, "contactPhone", "applicantPhone")
        );
        trustedBody.put(
                "contactPhone",
                submittedContactPhone == null ? contact.get("contactPhone") : submittedContactPhone
        );
        BookingResult result = createBookingRecord(
                JdbcMaps.requiredLong(trustedBody, "Meeting room is required", "roomId", "meetingRoomId"),
                null,
                principal.userId(),
                trustedBody,
                "PENDING",
                principal.userId()
        );
        if (mobileCustomFieldValueService != null) {
            mobileCustomFieldValueService.save(
                    "meeting-bookings",
                    result.id(),
                    body.get("customFields"),
                    principal.userId()
            );
        }
        return ApiResponse.success(Map.of(
                "id", result.id(),
                "bookingNumber", result.code(),
                "calculatedAmount", result.amount(),
                "status", "PENDING"
        ));
    }

    @PostMapping("/mobile/meetings/bookings")
    @Transactional
    public ApiResponse<Map<String, Object>> mobileCreateBooking(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @RequestBody Map<String, Object> body) {
        MobileApiSupport.requireCapability(principal, "meeting.book");
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        if (moduleFieldSubmissionValidator != null) {
            moduleFieldSubmissionValidator.validateMobileCreate(
                    "meeting-bookings",
                    body,
                    principal,
                    Set.of()
            );
        }
        Map<String, Object> contact = mobileBookingContact(principal.userId());
        Map<String, Object> trustedBody = new LinkedHashMap<>(body);
        trustedBody.remove("sourceType");
        trustedBody.remove("tenantId");
        trustedBody.remove("mobileUserId");
        trustedBody.put("contactName", contact.get("contactName"));
        trustedBody.put("contactPhone", contact.get("contactPhone"));
        BookingResult result = createBookingRecord(
                JdbcMaps.requiredLong(trustedBody, "Meeting room is required", "roomId", "meetingRoomId"),
                tenantId,
                principal.userId(),
                trustedBody,
                "PENDING",
                principal.userId()
        );
        if (mobileCustomFieldValueService != null) {
            mobileCustomFieldValueService.save(
                    "meeting-bookings",
                    result.id(),
                    body.get("customFields"),
                    principal.userId()
            );
        }
        return ApiResponse.success(Map.of(
                "id", result.id(),
                "bookingNumber", result.code(),
                "calculatedAmount", result.amount(),
                "unitPriceSnapshot", result.unitPriceSnapshot(),
                "status", "PENDING"
        ));
    }

    @PostMapping("/mobile/meetings/bookings/{id}/cancel")
    public ApiResponse<Void> mobileCancelBooking(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        MobileApiSupport.requireCapability(principal, "meeting.book");
        Long tenantId = MobileApiSupport.requireTenantId(principal);
        boolean companyScope = MobileApiSupport.isTenantManager(jdbcTemplate, principal);
        ensureUpdated(jdbcTemplate.update(
                """
                UPDATE meeting_booking
                SET booking_status = 'CANCELLED',
                    remark = COALESCE(?, remark),
                    updated_by = ?
                WHERE id = ?
                  AND tenant_id = ?
                  AND (? = 1 OR applicant_mobile_user_id = ?)
                  AND deleted = 0
                  AND booking_status NOT IN ('COMPLETED', 'CANCELLED', 'REJECTED')
                """,
                JdbcMaps.str(body, "cancelReason", "remark"),
                principal.userId(),
                id,
                tenantId,
                companyScope ? 1 : 0,
                principal.userId()
        ), "Booking not found");
        return ApiResponse.success();
    }

    private Map<String, Object> mobileBookingContact(Long mobileUserId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT contact_record.name AS contactName,
                       mobile.phone AS contactPhone
                FROM mobile_user mobile
                JOIN mobile_user_bind bind_record
                  ON bind_record.id = mobile.current_bind_id
                 AND bind_record.mobile_user_id = mobile.id
                 AND bind_record.bind_type = 'TENANT'
                 AND bind_record.status = 'ENABLED'
                 AND bind_record.deleted = 0
                JOIN tenant_contact contact_record
                  ON contact_record.id = bind_record.tenant_contact_id
                 AND contact_record.status IN ('ACTIVE', 'ENABLED')
                 AND contact_record.deleted = 0
                WHERE mobile.id = ?
                  AND mobile.status IN ('ACTIVE', 'ENABLED')
                  AND mobile.deleted = 0
                """,
                mobileUserId
        );
        if (rows.isEmpty()) {
            throw BusinessException.unauthorized("当前租户联系人绑定已失效");
        }
        return rows.getFirst();
    }

    private Map<String, Object> publicBookingContact(Long mobileUserId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT COALESCE(
                         NULLIF(TRIM(mobile.real_name), ''),
                         NULLIF(TRIM(mobile.nickname), ''),
                         '微信访客'
                       ) AS contactName,
                       mobile.phone AS contactPhone
                FROM mobile_user mobile
                WHERE mobile.id = ?
                  AND mobile.status IN ('ACTIVE', 'ENABLED')
                  AND mobile.deleted = 0
                  AND mobile.phone IS NOT NULL
                  AND TRIM(mobile.phone) <> ''
                  AND mobile.phone_verified_at IS NOT NULL
                LIMIT 1
                """,
                mobileUserId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(409, "请先完成微信手机号快捷登录后再预约会议室");
        }
        return rows.getFirst();
    }

    @PostMapping("/meeting-bookings/{id}/confirm")
    @Transactional
    public ApiResponse<Void> confirm(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> booking = reviewBooking(
                id,
                "CONFIRMED",
                reviewReply(body, false),
                principal
        );
        if (numberLong(booking.get("tenantId")) != null
                && decimal(booking.get("amount")).compareTo(BigDecimal.ZERO) > 0) {
            assetChargeBasisService.confirmMeetingBasis(id, principal.userId());
        }
        return ApiResponse.success();
    }

    @PostMapping("/meeting-bookings/{id}/reject")
    @Transactional
    public ApiResponse<Void> reject(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        reviewBooking(id, "REJECTED", reviewReply(body, true), principal);
        return ApiResponse.success();
    }

    @PostMapping("/meeting-bookings/{id}/cancel")
    public ApiResponse<Void> cancel(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        int updated = jdbcTemplate.update(
                """
                UPDATE meeting_booking
                SET booking_status = 'CANCELLED',
                    remark = COALESCE(?, remark),
                    updated_by = ?
                WHERE id = ?
                  AND deleted = 0
                  AND booking_status IN ('PENDING', 'BOOKED', 'CONFIRMED')
                """,
                JdbcMaps.str(body, "cancelReason", "remark"),
                principal.userId(),
                id
        );
        ensureBookingTransition(updated, id, "当前预约状态不能取消");
        return ApiResponse.success();
    }

    @PostMapping("/meeting-bookings/{id}/complete")
    public ApiResponse<Void> complete(
            @RequestAttribute(AuthInterceptor.PRINCIPAL_ATTRIBUTE) AuthPrincipal principal,
            @PathVariable Long id) {
        int updated = jdbcTemplate.update(
                """
                UPDATE meeting_booking
                SET booking_status = 'COMPLETED', updated_by = ?
                WHERE id = ? AND deleted = 0 AND booking_status = 'CONFIRMED'
                """,
                principal.userId(),
                id
        );
        ensureBookingTransition(updated, id, "会议预约审核通过后才能完成");
        return ApiResponse.success();
    }

    @GetMapping("/meeting-bills")
    public ApiResponse<List<Map<String, Object>>> meetingBills() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM meeting_bill WHERE deleted = 0 ORDER BY id DESC"));
    }

    private BookingResult createBookingRecord(
            Long roomId,
            Long tenantId,
            Long mobileUserId,
            Map<String, Object> body,
            String bookingStatus,
            Long operatorId) {
        Map<String, Object> room = lockMeetingRoom(roomId);
        LocalDateTime start = parseDateTime(JdbcMaps.requiredStr(body, "Start time is required", "startTime"));
        LocalDateTime end = parseDateTime(JdbcMaps.requiredStr(body, "End time is required", "endTime"));
        if (!end.isAfter(start)) {
            throw new BusinessException(400, "Meeting booking end time must be after its start time");
        }
        LocalTime openTime = localTime(room.get("open_time"));
        LocalTime closeTime = localTime(room.get("close_time"));
        if (openTime != null && start.toLocalTime().isBefore(openTime)) {
            throw new BusinessException(400, "Meeting booking starts before the room opens");
        }
        if (closeTime != null && end.toLocalTime().isAfter(closeTime)) {
            throw new BusinessException(400, "Meeting booking ends after the room closes");
        }
        Integer participantCount = JdbcMaps.intVal(body, null, "participantCount");
        if (participantCount == null || participantCount <= 0) {
            throw new BusinessException(400, "Participant count must be greater than zero");
        }
        int capacity = room.get("capacity") instanceof Number number ? number.intValue() : 0;
        if (participantCount != null && participantCount > capacity) {
            throw new BusinessException(400, "Participant count exceeds meeting room capacity");
        }
        String purpose = limitedStr(body, 500, "Meeting purpose cannot exceed 500 characters", "purpose");
        if (purpose == null) {
            throw new BusinessException(400, "Meeting purpose is required");
        }
        String contactName = limitedStr(body, 64, "Applicant name cannot exceed 64 characters",
                "contactName", "applicantName");
        if (contactName == null) {
            throw new BusinessException(400, "Applicant name is required");
        }
        String contactPhone = limitedStr(body, 32, "Contact phone cannot exceed 32 characters",
                "contactPhone", "applicantPhone");
        String departmentName = limitedStr(body, 100, "Department cannot exceed 100 characters",
                "departmentName", "department");
        String remark = limitedStr(body, 500, "Remark cannot exceed 500 characters", "remark");
        List<Long> conflicts = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM meeting_booking
                WHERE meeting_room_id = ?
                  AND deleted = 0
                  AND booking_status NOT IN ('CANCELLED', 'REJECTED')
                  AND start_time < ?
                  AND end_time > ?
                LIMIT 1
                FOR UPDATE
                """,
                Long.class,
                roomId,
                end,
                start
        );
        if (!conflicts.isEmpty()) {
            throw new BusinessException(409, "The meeting room is already booked for an overlapping time");
        }
        boolean internalBooking = "INTERNAL".equalsIgnoreCase(JdbcMaps.str(body, "sourceType"));
        Map<String, Object> rule = internalBooking ? null : activeRateRule(roomId, start.toLocalDate());
        BigDecimal legacyHourlyPrice = decimal(room.get("hourly_price"));
        String chargeType = rule == null
                ? (legacyHourlyPrice.signum() > 0 ? "HOURLY" : "FREE")
                : String.valueOf(rule.get("charge_type"));
        Long ruleId = rule == null ? null : numberLong(rule.get("id"));
        BigDecimal unitPrice = "FREE".equals(chargeType)
                ? BigDecimal.ZERO
                : (rule == null ? legacyHourlyPrice : decimal(rule.get("hourly_price")));
        int minimumMinutes = rule == null || !(rule.get("minimum_minutes") instanceof Number number)
                ? 60
                : number.intValue();
        long actualMinutes = Duration.between(start, end).toMinutes();
        long billingMinutes = "FREE".equals(chargeType) ? actualMinutes : Math.max(actualMinutes, minimumMinutes);
        BigDecimal amount = unitPrice
                .multiply(BigDecimal.valueOf(billingMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        String code = JdbcMaps.code("MBK");
        jdbcTemplate.update(
                """
                INSERT INTO meeting_booking (
                    code, meeting_room_id, tenant_id, applicant_mobile_user_id,
                    start_time, end_time, purpose, participant_count, contact_name, contact_phone,
                    department_name, pricing_rule_id, unit_price_snapshot, duration_minutes, amount,
                    booking_status, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                code,
                roomId,
                tenantId,
                mobileUserId,
                start,
                end,
                purpose,
                participantCount,
                contactName,
                contactPhone,
                departmentName,
                ruleId,
                unitPrice,
                billingMinutes,
                amount,
                bookingStatus,
                operatorId,
                operatorId,
                remark
        );
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM meeting_booking WHERE code = ? AND deleted = 0 LIMIT 1",
                Long.class,
                code
        );
        return new BookingResult(id, code, unitPrice, amount);
    }

    private String limitedStr(Map<String, Object> body, int maxLength, String message, String... keys) {
        String value = JdbcMaps.str(body, keys);
        if (value != null && value.length() > maxLength) {
            throw new BusinessException(400, message);
        }
        return value;
    }

    private Map<String, Object> lockMeetingRoom(Long roomId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, capacity, open_time, close_time, hourly_price
                FROM meeting_room
                WHERE id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED', 'AVAILABLE')
                FOR UPDATE
                """,
                roomId
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "Meeting room not found or disabled");
        }
        return rows.getFirst();
    }

    private Map<String, Object> activeRateRule(Long roomId, LocalDate businessDate) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id, charge_type, hourly_price, minimum_minutes
                FROM meeting_rate_rule
                WHERE meeting_room_id = ?
                  AND deleted = 0
                  AND status = 'ACTIVE'
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                ORDER BY effective_from DESC, id DESC
                LIMIT 1
                """,
                roomId,
                businessDate,
                businessDate
        );
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private RateRuleInput rateRuleInput(Map<String, Object> body) {
        String chargeType = JdbcMaps.strOr(body, "HOURLY", "chargeType").trim().toUpperCase();
        if (!List.of("FREE", "HOURLY").contains(chargeType)) {
            throw new BusinessException(400, "Meeting charge type must be FREE or HOURLY");
        }
        BigDecimal price = "FREE".equals(chargeType)
                ? BigDecimal.ZERO
                : JdbcMaps.decimal(body, null, "hourlyPrice", "price");
        if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "Meeting hourly price cannot be negative");
        }
        Integer minimumMinutes = JdbcMaps.intVal(body, 60, "minimumMinutes");
        if (minimumMinutes == null || minimumMinutes <= 0) {
            throw new BusinessException(400, "Minimum booking duration must be greater than zero");
        }
        LocalDate effectiveFrom = LocalDate.parse(
                JdbcMaps.requiredStr(body, "Effective date is required", "effectiveFrom")
        );
        String effectiveToValue = JdbcMaps.str(body, "effectiveTo");
        LocalDate effectiveTo = effectiveToValue == null ? null : LocalDate.parse(effectiveToValue);
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new BusinessException(400, "Rule end date cannot be before its start date");
        }
        return new RateRuleInput(
                chargeType,
                price,
                minimumMinutes,
                effectiveFrom,
                effectiveTo,
                JdbcMaps.str(body, "remark")
        );
    }

    private void ensureRateRuleNoOverlap(Long roomId, LocalDate effectiveFrom, LocalDate effectiveTo) {
        Long overlaps = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM meeting_rate_rule
                WHERE meeting_room_id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'RETIRED')
                  AND effective_from <= ?
                  AND (effective_to IS NULL OR effective_to >= ?)
                """,
                Long.class,
                roomId,
                effectiveTo == null ? LocalDate.of(9999, 12, 31) : effectiveTo,
                effectiveFrom
        );
        if (overlaps != null && overlaps > 0) {
            throw new BusinessException(409, "Meeting rate rule effective periods cannot overlap");
        }
    }

    private Long insertRateRule(Long roomId, RateRuleInput input) {
        String code = JdbcMaps.code("MRR");
        jdbcTemplate.update(
                """
                INSERT INTO meeting_rate_rule (
                    code, meeting_room_id, charge_type, hourly_price, minimum_minutes,
                    effective_from, effective_to, status, created_by, updated_by, remark
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """,
                code,
                roomId,
                input.chargeType(),
                input.hourlyPrice(),
                input.minimumMinutes(),
                input.effectiveFrom(),
                input.effectiveTo(),
                0L,
                0L,
                input.remark()
        );
        return jdbcTemplate.queryForObject(
                "SELECT id FROM meeting_rate_rule WHERE code = ? AND deleted = 0 LIMIT 1",
                Long.class,
                code
        );
    }

    private void saveMeetingRoom(Long id, Map<String, Object> body) {
        String name = JdbcMaps.requiredStr(body, "Meeting room name is required", "name");
        BigDecimal price = JdbcMaps.decimal(body, BigDecimal.ZERO, "workdayHourlyRate", "hourlyPrice");
        if (price.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "Meeting room price cannot be negative");
        }
        String images = jsonValue(body.get("imageFileIds"));
        if (id == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO meeting_room (
                        code, name, capacity, location, facilities, description,
                        open_time, close_time, image_file_ids, hourly_price,
                        status, created_by, updated_by, remark
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    JdbcMaps.strOr(body, JdbcMaps.code("MR"), "code", "roomCode"),
                    name,
                    JdbcMaps.intVal(body, 0, "capacity"),
                    JdbcMaps.str(body, "location"),
                    JdbcMaps.str(body, "facilities", "equipment"),
                    JdbcMaps.str(body, "description"),
                    JdbcMaps.str(body, "openTime"),
                    JdbcMaps.str(body, "closeTime"),
                    images,
                    price,
                    JdbcMaps.strOr(body, "AVAILABLE", "status"),
                    0L,
                    0L,
                    JdbcMaps.str(body, "remark")
            );
            return;
        }
        ensureUpdated(jdbcTemplate.update(
                """
                UPDATE meeting_room
                SET name = ?,
                    capacity = ?,
                    location = ?,
                    facilities = ?,
                    description = ?,
                    open_time = ?,
                    close_time = ?,
                    image_file_ids = ?,
                    hourly_price = ?,
                    status = ?,
                    updated_by = ?,
                    remark = ?
                WHERE id = ? AND deleted = 0
                """,
                name,
                JdbcMaps.intVal(body, 0, "capacity"),
                JdbcMaps.str(body, "location"),
                JdbcMaps.str(body, "facilities", "equipment"),
                JdbcMaps.str(body, "description"),
                JdbcMaps.str(body, "openTime"),
                JdbcMaps.str(body, "closeTime"),
                images,
                price,
                JdbcMaps.strOr(body, "AVAILABLE", "status"),
                0L,
                JdbcMaps.str(body, "remark"),
                id
        ), "Meeting room not found");
    }

    private Map<String, Object> reviewBooking(
            Long id,
            String targetStatus,
            String reply,
            AuthPrincipal principal) {
        if (principal == null || principal.userId() == null) {
            throw BusinessException.unauthorized("登录状态失效，请重新登录");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT booking_status AS status, amount, tenant_id AS tenantId
                FROM meeting_booking
                WHERE id = ? AND deleted = 0
                FOR UPDATE
                """,
                id
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "预约记录不存在");
        }
        Map<String, Object> booking = rows.getFirst();
        String currentStatus = String.valueOf(booking.get("status"));
        if (!"PENDING".equals(currentStatus) && !"BOOKED".equals(currentStatus)) {
            throw new BusinessException(409, "该预约已审核或已结束，请刷新后重试");
        }
        int updated = jdbcTemplate.update(
                """
                UPDATE meeting_booking
                SET booking_status = ?,
                    review_reply = ?,
                    reviewed_by = ?,
                    reviewed_at = NOW(),
                    updated_by = ?
                WHERE id = ?
                  AND deleted = 0
                  AND booking_status IN ('PENDING', 'BOOKED')
                """,
                targetStatus,
                reply,
                principal.userId(),
                principal.userId(),
                id
        );
        if (updated != 1) {
            throw new BusinessException(409, "预约状态已变化，请刷新后重试");
        }
        return booking;
    }

    private String reviewReply(Map<String, Object> body, boolean required) {
        String reply = JdbcMaps.str(body, "reviewReply", "reply");
        if (required && reply == null) {
            throw new BusinessException(400, "驳回预约时必须填写回复");
        }
        if (reply != null && reply.length() > 500) {
            throw new BusinessException(400, "预约回复不能超过500个字符");
        }
        return reply == null ? "预约已审核通过" : reply;
    }

    private BigDecimal calculateAmount(Long roomId, String startTime, String endTime) {
        try {
            LocalDateTime start = parseDateTime(startTime);
            LocalDateTime end = parseDateTime(endTime);
            if (!end.isAfter(start)) {
                return BigDecimal.ZERO;
            }
            Map<String, Object> rule = activeRateRule(roomId, start.toLocalDate());
            if (rule == null || "FREE".equals(String.valueOf(rule.get("charge_type")))) {
                return BigDecimal.ZERO;
            }
            long actualMinutes = Duration.between(start, end).toMinutes();
            int minimumMinutes = rule.get("minimum_minutes") instanceof Number number ? number.intValue() : 60;
            long billingMinutes = Math.max(actualMinutes, minimumMinutes);
            return decimal(rule.get("hourly_price"))
                    .multiply(BigDecimal.valueOf(billingMinutes))
                    .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        } catch (Exception ignored) {
            return BigDecimal.ZERO;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.replace(' ', 'T'));
        } catch (RuntimeException exception) {
            throw new BusinessException(400, "Meeting time must use YYYY-MM-DD HH:mm:ss");
        }
    }

    private LocalTime localTime(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Time time) return time.toLocalTime();
        String text = String.valueOf(value);
        return LocalTime.parse(text.length() >= 8 ? text.substring(0, 8) : text);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return new BigDecimal(String.valueOf(value));
    }

    private Long numberLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private String jsonValue(Object value) {
        if (value == null) return null;
        if (value instanceof String text) return text;
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new BusinessException(400, "Meeting room image list is invalid");
        }
    }

    private String normalizeDateTime(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.replace('T', ' ');
    }

    private boolean isTruthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }

    private void ensureBookingTransition(int updated, Long id, String conflictMessage) {
        if (updated > 0) return;
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM meeting_booking WHERE id = ? AND deleted = 0",
                Integer.class,
                id
        );
        if (exists == null || exists == 0) {
            throw new BusinessException(404, "预约记录不存在");
        }
        throw new BusinessException(409, conflictMessage);
    }

    private record RateRuleInput(
            String chargeType,
            BigDecimal hourlyPrice,
            Integer minimumMinutes,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String remark) {
    }

    private record BookingResult(Long id, String code, BigDecimal unitPriceSnapshot, BigDecimal amount) {
    }
}
