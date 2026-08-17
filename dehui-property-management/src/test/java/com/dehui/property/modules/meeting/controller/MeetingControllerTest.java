package com.dehui.property.modules.meeting.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.asset.application.AssetChargeBasisService;
import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.modules.customfield.application.ModuleFieldRegistry;
import com.dehui.property.modules.customfield.application.ModuleFieldSchemaService;
import com.dehui.property.modules.customfield.application.ModuleFieldSubmissionValidator;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.dehui.property.security.AuthPrincipal;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class MeetingControllerTest {

    @Test
    void internalBookingCalculationIsAlwaysFree() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        ApiResponse<Map<String, Object>> response = new MeetingController(jdbcTemplate)
                .calculate("INTERNAL", 1L, "2026-05-30T10:00:00", "2026-05-30T12:00:00");

        assertEquals("INTERNAL_FREE", response.data().get("feeType"));
        assertEquals(BigDecimal.ZERO, response.data().get("amount"));
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void tenantBookingCalculationUsesEffectiveRateRuleSnapshot() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                org.mockito.ArgumentMatchers.contains("FROM meeting_rate_rule"),
                org.mockito.ArgumentMatchers.any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "id", 9L,
                "charge_type", "HOURLY",
                "hourly_price", new BigDecimal("80.00"),
                "minimum_minutes", 60
        )));

        ApiResponse<Map<String, Object>> response = new MeetingController(jdbcTemplate)
                .calculate("TENANT", 3L, "2026-05-30T10:00:00", "2026-05-30T11:30:00");

        assertEquals("HOURLY", response.data().get("feeType"));
        assertEquals(new BigDecimal("120.00"), response.data().get("amount"));
    }

    @Test
    void bookingListReturnsOnlyPersistedOrTraceableRelationFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        new MeetingController(jdbcTemplate).meetingBookings();

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("t.name AS tenantName"));
        assertTrue(sql.contains("mu.nickname"));
        assertTrue(sql.contains("NULLIF(b.contact_name, '')"));
        assertTrue(sql.contains("AS applicantName"));
        assertTrue(sql.contains("b.department_name AS departmentName"));
        assertTrue(sql.contains("b.review_reply AS reviewReply"));
        assertTrue(sql.contains("reviewer.real_name"));
        assertTrue(sql.contains("b.reviewed_at AS reviewedAt"));
        assertTrue(sql.contains("b.amount"));
        assertTrue(sql.contains("basis_record.bill_id AS billingId"));
        assertTrue(sql.contains("gb.pay_status"));
        assertTrue(sql.contains("WHEN b.applicant_mobile_user_id IS NOT NULL THEN 'PUBLIC'"));
        assertTrue(sql.contains("WHEN b.tenant_id IS NULL AND b.applicant_mobile_user_id IS NULL THEN 'INTERNAL_FREE'"));
        assertTrue(sql.contains("b.created_at AS createdAt"));
        assertTrue(sql.contains("WHERE b.deleted = 0"));
        assertTrue(sql.contains("ORDER BY b.created_at DESC, b.id DESC"));
        assertFalse(sql.contains("b.booking_status <> 'CANCELLED'"));
        assertFalse(sql.contains("b.booking_status NOT IN ('CANCELLED'"));
    }

    @Test
    void cancellingBookingRetainsTheRecordForAudit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        new MeetingController(jdbcTemplate).cancel(
                adminPrincipal(),
                88L,
                Map.of("cancelReason", "申请人取消")
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("SET booking_status = 'CANCELLED'"));
        assertTrue(sql.contains("AND deleted = 0"));
        assertFalse(sql.contains("deleted = 1"));
        assertFalse(sql.contains("DELETE FROM meeting_booking"));
    }

    @Test
    void verifiedGuestCanonicalPayloadPassesRealValidatorAndUsesSubmittedContactPhone() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MobileIdentityService identityService = mock(MobileIdentityService.class);
        stubGuestBookingQueries(jdbcTemplate, new BigDecimal("100.00"));
        when(identityService.bindingState(21L, "PUBLIC")).thenReturn("UNMATCHED");
        MeetingController controller = controller(jdbcTemplate, identityService);
        installRealMeetingValidator(controller);

        ApiResponse<Map<String, Object>> response = controller
                .publicCreateBooking(guestPrincipal(), Map.ofEntries(
                        Map.entry("meetingRoomId", 7L),
                        Map.entry("startTime", "2026-08-03T10:00:00"),
                        Map.entry("endTime", "2026-08-03T11:00:00"),
                        Map.entry("purpose", "产品交流"),
                        Map.entry("participantCount", 4),
                        Map.entry("contactName", "王访客"),
                        Map.entry("contactPhone", "13999999999"),
                        Map.entry("departmentName", "研发部"),
                        Map.entry("remark", "需要投影")
                ));

        assertEquals("PENDING", response.data().get("status"));
        assertEquals(new BigDecimal("100.00"), response.data().get("calculatedAmount"));
        assertFalse(response.data().containsKey("unitPriceSnapshot"));
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(argThat((String sql) -> sql != null
                && sql.contains("INSERT INTO meeting_booking")), args.capture());
        Object[] inserted = args.getValue();
        assertEquals(7L, inserted[1]);
        assertEquals(null, inserted[2]);
        assertEquals(21L, inserted[3]);
        assertEquals("王访客", inserted[8]);
        assertEquals("13999999999", inserted[9]);
        assertEquals("PENDING", inserted[15]);
        assertEquals(21L, inserted[16]);
        assertEquals(21L, inserted[17]);
        assertFalse(java.util.Arrays.asList(inserted).contains("13800001234"));
    }

    @Test
    void realValidatorRejectsGuestForgedRelationshipIdsBeforeInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MobileIdentityService identityService = mock(MobileIdentityService.class);
        when(identityService.bindingState(21L, "PUBLIC")).thenReturn("UNMATCHED");
        MeetingController controller = controller(jdbcTemplate, identityService);
        installRealMeetingValidator(controller);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller.publicCreateBooking(guestPrincipal(), Map.ofEntries(
                        Map.entry("meetingRoomId", 7L),
                        Map.entry("startTime", "2026-08-03T10:00:00"),
                        Map.entry("endTime", "2026-08-03T11:00:00"),
                        Map.entry("purpose", "产品交流"),
                        Map.entry("participantCount", 4),
                        Map.entry("contactName", "王访客"),
                        Map.entry("tenantId", 999L)
                )));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("tenantId"));
        verify(jdbcTemplate, never()).update(
                argThat((String sql) -> sql != null && sql.contains("INSERT INTO meeting_booking")),
                any(Object[].class)
        );
    }

    @Test
    void guestBookingRechecksPhoneVerificationEvenWithStaleCapability() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MobileIdentityService identityService = mock(MobileIdentityService.class);
        when(identityService.bindingState(21L, "PUBLIC")).thenReturn("REVERIFY_REQUIRED");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller(jdbcTemplate, identityService).publicCreateBooking(
                        guestPrincipal(),
                        validGuestBody()
                ));

        assertEquals(409, exception.getCode());
        verify(jdbcTemplate, never()).update(
                argThat((String sql) -> sql != null && sql.contains("INSERT INTO meeting_booking")),
                any(Object[].class)
        );
    }

    @Test
    void guestBookingRejectsPendingBusinessIdentitySelection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MobileIdentityService identityService = mock(MobileIdentityService.class);
        when(identityService.bindingState(21L, "PUBLIC")).thenReturn("PENDING_SELECTION");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> controller(jdbcTemplate, identityService).publicCreateBooking(
                        guestPrincipal(),
                        validGuestBody()
                ));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("选择租户或物业身份"));
    }

    @Test
    void guestBookingFallsBackToProfileNameWhenContactNameMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MobileIdentityService identityService = mock(MobileIdentityService.class);
        stubGuestBookingQueries(jdbcTemplate, BigDecimal.ZERO);
        when(identityService.bindingState(21L, "PUBLIC")).thenReturn("UNMATCHED");
        MeetingController controller = controller(jdbcTemplate, identityService);
        installRealMeetingValidator(controller);

        ApiResponse<Map<String, Object>> response =
                controller.publicCreateBooking(guestPrincipal(), validGuestBody(false));

        assertEquals("PENDING", response.data().get("status"));
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("INSERT INTO meeting_booking")),
                args.capture()
        );
        assertEquals("微信访客", args.getValue()[8]);
        assertEquals("13800001234", args.getValue()[9]);
    }

    @Test
    void verifiedGuestRejectsInvalidSubmittedContactPhone() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MobileIdentityService identityService = mock(MobileIdentityService.class);
        stubGuestBookingQueries(jdbcTemplate, BigDecimal.ZERO);
        when(identityService.bindingState(21L, "PUBLIC")).thenReturn("UNMATCHED");
        MeetingController controller = controller(jdbcTemplate, identityService);
        installRealMeetingValidator(controller);
        Map<String, Object> body = new HashMap<>(validGuestBody());
        body.put("contactPhone", "12345");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.publicCreateBooking(guestPrincipal(), body)
        );

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("联系电话格式不正确"));
        verify(jdbcTemplate, never()).update(
                argThat((String sql) -> sql != null && sql.contains("INSERT INTO meeting_booking")),
                any(Object[].class)
        );
    }

    @Test
    void activeTenantContactCanCreateMobileBooking() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null
                        && sql.contains("FROM mobile_user mobile")
                        && sql.contains("contact_record.status IN ('ACTIVE', 'ENABLED')")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "contactName", "张昭",
                "contactPhone", "16600007751"
        )));
        stubBookingQueries(jdbcTemplate, BigDecimal.ZERO);

        ApiResponse<Map<String, Object>> response = controller(
                jdbcTemplate,
                mock(MobileIdentityService.class)
        ).mobileCreateBooking(tenantPrincipal(), validGuestBody(false));

        assertEquals(200, response.code());
        assertEquals("PENDING", response.data().get("status"));
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("INSERT INTO meeting_booking")),
                args.capture()
        );
        assertEquals(17L, args.getValue()[2]);
        assertEquals(31L, args.getValue()[3]);
        assertEquals("张昭", args.getValue()[8]);
        assertEquals("16600007751", args.getValue()[9]);
        assertEquals("PENDING", args.getValue()[15]);
    }

    @Test
    void confirmingChargedPublicBookingDoesNotCreateTenantChargeBasis() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AssetChargeBasisService chargeBasisService = mock(AssetChargeBasisService.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        Map<String, Object> publicBooking = new HashMap<>();
        publicBooking.put("amount", new BigDecimal("100.00"));
        publicBooking.put("tenantId", null);
        publicBooking.put("status", "PENDING");
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("tenant_id AS tenantId")),
                any(Object[].class)
        )).thenReturn(List.of(publicBooking));

        ApiResponse<Void> response = controller(
                jdbcTemplate,
                mock(MobileIdentityService.class),
                chargeBasisService
        ).confirm(adminPrincipal(), 88L, Map.of("reviewReply", "预约已审核通过"));

        assertEquals(200, response.code());
        verify(chargeBasisService, never()).confirmMeetingBasis(any(), any());
    }

    @Test
    void confirmingChargedTenantBookingStillCreatesChargeBasis() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AssetChargeBasisService chargeBasisService = mock(AssetChargeBasisService.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("tenant_id AS tenantId")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "amount", new BigDecimal("100.00"),
                "tenantId", 6L,
                "status", "PENDING"
        )));

        controller(
                jdbcTemplate,
                mock(MobileIdentityService.class),
                chargeBasisService
        ).confirm(adminPrincipal(), 89L, Map.of("reviewReply", "会议室已为您保留"));

        verify(chargeBasisService).confirmMeetingBasis(89L, 9L);
    }

    @Test
    void approvingBookingStoresReplyAndServerReviewer() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FOR UPDATE")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "status", "PENDING",
                "amount", BigDecimal.ZERO
        )));

        controller(jdbcTemplate, mock(MobileIdentityService.class))
                .confirm(adminPrincipal(), 91L, Map.of("reviewReply", "审核通过，请按时使用"));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null
                        && sql.contains("review_reply = ?")
                        && sql.contains("reviewed_by = ?")
                        && sql.contains("booking_status IN ('PENDING', 'BOOKED')")),
                args.capture()
        );
        assertEquals("CONFIRMED", args.getValue()[0]);
        assertEquals("审核通过，请按时使用", args.getValue()[1]);
        assertEquals(9L, args.getValue()[2]);
        assertEquals(9L, args.getValue()[3]);
        assertEquals(91L, args.getValue()[4]);
    }

    @Test
    void rejectingBookingRequiresReply() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller(jdbcTemplate, mock(MobileIdentityService.class))
                        .reject(adminPrincipal(), 92L, Map.of())
        );

        assertEquals(400, exception.getCode());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectingBookingStoresReplyAndServerReviewer() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FOR UPDATE")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "status", "BOOKED",
                "amount", BigDecimal.ZERO
        )));

        controller(jdbcTemplate, mock(MobileIdentityService.class))
                .reject(adminPrincipal(), 93L, Map.of("reviewReply", "该时间段正在使用，请选择其他时间"));

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("review_reply = ?")),
                args.capture()
        );
        assertEquals("REJECTED", args.getValue()[0]);
        assertEquals("该时间段正在使用，请选择其他时间", args.getValue()[1]);
        assertEquals(9L, args.getValue()[2]);
        assertEquals(9L, args.getValue()[3]);
        assertEquals(93L, args.getValue()[4]);
    }

    private MeetingController controller(
            JdbcTemplate jdbcTemplate,
            MobileIdentityService identityService) {
        return controller(jdbcTemplate, identityService, mock(AssetChargeBasisService.class));
    }

    private MeetingController controller(
            JdbcTemplate jdbcTemplate,
            MobileIdentityService identityService,
            AssetChargeBasisService chargeBasisService) {
        return new MeetingController(
                jdbcTemplate,
                chargeBasisService,
                null,
                null,
                identityService
        );
    }

    private AuthPrincipal guestPrincipal() {
        return new AuthPrincipal(
                21L,
                "mobile:21",
                "PUBLIC",
                null,
                List.of("MOBILE_USER"),
                List.of("meeting.public.view", "meeting.guest.book")
        );
    }

    private AuthPrincipal tenantPrincipal() {
        return new AuthPrincipal(
                31L,
                "mobile:31",
                "TENANT",
                17L,
                List.of("MOBILE_USER", "MOBILE_TENANT"),
                List.of("meeting.book")
        );
    }

    private AuthPrincipal adminPrincipal() {
        return new AuthPrincipal(
                9L,
                "meeting-reviewer",
                "SYSTEM",
                null,
                List.of("PROPERTY_MANAGER"),
                List.of("meeting-booking:view", "meeting-booking:confirm")
        );
    }

    private Map<String, Object> validGuestBody() {
        return validGuestBody(true);
    }

    private Map<String, Object> validGuestBody(boolean includeContactName) {
        Map<String, Object> body = new HashMap<>();
        body.put("meetingRoomId", 7L);
        body.put("startTime", "2026-08-03T10:00:00");
        body.put("endTime", "2026-08-03T11:00:00");
        body.put("purpose", "产品交流");
        body.put("participantCount", 4);
        if (includeContactName) body.put("contactName", "王访客");
        return body;
    }

    private void installRealMeetingValidator(MeetingController controller) {
        CustomFieldApplicationService customFields = mock(CustomFieldApplicationService.class);
        when(customFields.definitions(
                eq("meeting-bookings"),
                eq(false),
                any(AuthPrincipal.class)
        )).thenReturn(List.of());
        ModuleFieldSchemaService schemas = new ModuleFieldSchemaService(
                new ModuleFieldRegistry(),
                customFields
        );
        controller.setModuleFieldSubmissionValidator(new ModuleFieldSubmissionValidator(schemas));
    }

    private void stubGuestBookingQueries(JdbcTemplate jdbcTemplate, BigDecimal hourlyPrice) {
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null
                        && sql.contains("FROM mobile_user mobile")
                        && sql.contains("phone_verified_at IS NOT NULL")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "contactName", "微信访客",
                "contactPhone", "13800001234"
        )));
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null
                        && sql.contains("FROM meeting_room")
                        && sql.contains("FOR UPDATE")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "id", 7L,
                "capacity", 12,
                "open_time", "08:00:00",
                "close_time", "20:00:00",
                "hourly_price", hourlyPrice
        )));
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM meeting_rate_rule")),
                any(Object[].class)
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM meeting_booking")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("SELECT id FROM meeting_booking")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(88L);
    }

    private void stubBookingQueries(JdbcTemplate jdbcTemplate, BigDecimal hourlyPrice) {
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null
                        && sql.contains("FROM meeting_room")
                        && sql.contains("FOR UPDATE")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "id", 7L,
                "capacity", 12,
                "open_time", "08:00:00",
                "close_time", "20:00:00",
                "hourly_price", hourlyPrice
        )));
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM meeting_rate_rule")),
                any(Object[].class)
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM meeting_booking")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(List.of());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(
                argThat((String sql) -> sql != null && sql.contains("SELECT id FROM meeting_booking")),
                eq(Long.class),
                any(Object[].class)
        )).thenReturn(88L);
    }
}
