package com.dehui.property.modules.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.bill.controller.BillController;
import com.dehui.property.modules.checkin.controller.CheckinController;
import com.dehui.property.modules.meeting.controller.MeetingController;
import com.dehui.property.modules.mobile.controller.MobileApiSupport;
import com.dehui.property.modules.mobile.controller.MobileProfileController;
import com.dehui.property.modules.notice.controller.NoticeController;
import com.dehui.property.modules.visitor.controller.VisitorController;
import com.dehui.property.modules.workorder.controller.WorkOrderController;
import com.dehui.property.security.AuthPrincipal;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MobileEndpointCompatibilityTest {

    @Test
    void mineSummaryReturnsTenantDashboardShape() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 8L, "name", "德汇咖啡服务有限公司")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(4L, 12L, 6L, 3L, 2L, 1L, 5L);

        ApiResponse<Map<String, Object>> response = new MobileProfileController(jdbcTemplate)
                .summary(tenantPrincipal());

        assertEquals(200, response.code());
        assertFalse((Boolean) response.data().get("needTenantBind"));
        assertEquals(4L, response.data().get("announcementCount"));
        assertEquals(12L, response.data().get("availableRoomCount"));
        assertEquals(6L, response.data().get("investmentLeadCount"));
        assertEquals(3L, response.data().get("billCount"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileMeReturnsBackendTenantCapabilitiesInProfile() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> identity = new HashMap<>();
        identity.put("mobileUserId", 7L);
        identity.put("phone", "13800001234");
        identity.put("phoneVerifiedAt", LocalDateTime.of(2026, 7, 30, 10, 0));
        identity.put("declaredUserType", "TENANT");
        identity.put("currentBindType", "TENANT");
        identity.put("tenantId", 8L);
        identity.put("tenantContactId", 17L);
        identity.put("tenantStatus", "ACTIVE");
        identity.put("contactStatus", "ACTIVE");
        identity.put("contactPhone", "13800001234");
        identity.put("contactRole", "EMPLOYEE");
        identity.put("tenantLogoFileId", 44L);
        identity.put("tenantLogoFileName", "tenant-logo.webp");
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user mobile")),
                any(Object[].class)
        )).thenReturn(List.of(identity));

        ApiResponse<Map<String, Object>> response = new MobileProfileController(jdbcTemplate)
                .me(tenantPrincipal());
        assertEquals(List.of(
                "announcement.view",
                "investment.view",
                "investment.booking.create",
                "meeting.public.view",
                "my-business.view",
                "workorder.create",
                "workorder.view",
                "meeting.book",
                "meeting.mine.view",
                "visitor.create",
                "visitor.mine.view",
                "parking.mine.view"
        ), response.data().get("capabilities"));
        Map<String, Object> tenantInfo = (Map<String, Object>) response.data().get("tenantInfo");
        assertEquals("tenant-logo.webp", tenantInfo.get("logoFileName"));
        assertEquals("/api/mobile/files/44", tenantInfo.get("logoUrl"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void invalidInternalBindingIsImmediatelyRevokedToPublic() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user mobile")),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "mobileUserId", 9L,
                "declaredUserType", "INTERNAL",
                "boundSysUserId", 3L,
                "staffStatus", "ENABLED"
        )));

        ApiResponse<Map<String, Object>> response = new MobileProfileController(jdbcTemplate)
                .me(staffPrincipal());
        assertEquals("PUBLIC", response.data().get("userType"));
        assertEquals(List.of(
                "announcement.view",
                "investment.view",
                "investment.booking.create",
                "meeting.public.view"
        ), response.data().get("capabilities"));
    }

    @Test
    void unverifiedBlankPhoneCannotRestoreHistoricalStaffBinding() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> identity = new HashMap<>();
        identity.put("mobileUserId", 53L);
        identity.put("phone", "");
        identity.put("phoneVerifiedAt", null);
        identity.put("declaredUserType", "INTERNAL");
        identity.put("currentBindType", "INTERNAL");
        identity.put("boundSysUserId", 1L);
        identity.put("staffPhone", "");
        identity.put("staffStatus", "ENABLED");
        identity.put("mobileEnabled", 1);
        identity.put("hasMobileWorkbenchAccess", 1);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user mobile")),
                any(Object[].class)
        )).thenReturn(List.of(identity));

        AuthPrincipal principal = MobileApiSupport.currentPrincipal(jdbcTemplate, 53L).orElseThrow();

        assertEquals("PUBLIC", principal.userType());
        assertFalse(principal.roles().contains("MOBILE_STAFF"));
    }

    @Test
    void legacyBackfilledVerificationTimestampCannotRestoreHistoricalStaffBinding() {
        AuthPrincipal principal = principalFor(staffIdentity(
                "13800001234",
                LocalDateTime.of(2026, 7, 28, 10, 0),
                "LEGACY",
                "13800001234"
        ));

        assertEquals("PUBLIC", principal.userType());
        assertFalse(principal.roles().contains("MOBILE_STAFF"));
    }

    @Test
    void verifiedMatchingPhoneKeepsStaffBinding() {
        AuthPrincipal principal = principalFor(staffIdentity(
                "+86 138-0000-1234",
                LocalDateTime.of(2026, 7, 30, 10, 0),
                "WECHAT_API",
                "13800001234"
        ));

        assertEquals("STAFF", principal.userType());
        assertTrue(principal.roles().contains("MOBILE_STAFF"));
    }

    @Test
    void verifiedMismatchedPhoneRevokesStaffBinding() {
        AuthPrincipal principal = principalFor(staffIdentity(
                "13800001234",
                LocalDateTime.of(2026, 7, 30, 10, 0),
                "WECHAT_API",
                "13900001234"
        ));

        assertEquals("PUBLIC", principal.userType());
        assertFalse(principal.roles().contains("MOBILE_STAFF"));
    }

    @Test
    void staffWriteBoundaryRejectsLegacyVerificationEvenIfBindingAndRbacStillLookActive() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Map<String, Object> identity = staffIdentity(
                "13800001234",
                LocalDateTime.of(2026, 7, 28, 10, 0),
                "LEGACY",
                "13800001234"
        );
        identity.put("sysUserId", 1L);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user mobile")),
                any(Object[].class)
        )).thenReturn(List.of(identity));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> MobileApiSupport.requireStaffUserId(jdbcTemplate, staffPrincipal())
        );

        assertEquals(403, exception.getCode());
    }

    @Test
    void missingPhoneVerificationRevokesTenantBinding() {
        AuthPrincipal principal = principalFor(tenantIdentity(
                "13800001234",
                null,
                "13800001234"
        ));

        assertEquals("PUBLIC", principal.userType());
        assertFalse(principal.roles().contains("MOBILE_TENANT"));
    }

    @Test
    void verifiedMatchingPhoneKeepsTenantBinding() {
        AuthPrincipal principal = principalFor(tenantIdentity(
                "0086(138)0000-1234",
                LocalDateTime.of(2026, 7, 30, 10, 0),
                "13800001234"
        ));

        assertEquals("TENANT", principal.userType());
        assertTrue(principal.roles().contains("MOBILE_TENANT"));
    }

    @Test
    void mobileBillsReturnProfileSummaryAndBills() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM mobile_user mobile"), eq(7L)))
                .thenReturn(List.of(tenantManagerIdentity()));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", 21L,
                        "billNumber", "BIL-001",
                        "billType", "RENT",
                        "amount", new BigDecimal("1200.00"),
                        "paidAmount", BigDecimal.ZERO,
                        "dueDate", "2026-06-30",
                        "status", "UNPAID",
                        "invoiceStatus", "INVOICED",
                        "invoiceFileName", "invoice-21.pdf",
                        "hasInvoiceFile", 1
                )));
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(Map.of("id", 8L, "name", "德汇咖啡服务有限公司"));

        ApiResponse<Map<String, Object>> response = new BillController(jdbcTemplate)
                .mobileBills(tenantPrincipal(), "UNPAID");

        assertEquals(200, response.code());
        assertTrue(response.data().containsKey("profile"));
        assertTrue(response.data().containsKey("summary"));
        assertTrue(response.data().containsKey("bills"));
        Map<String, Object> bill = ((List<Map<String, Object>>) response.data().get("bills")).getFirst();
        assertEquals(true, bill.get("hasInvoiceFile"));
        assertEquals("/mobile/bills/21/invoice/download", bill.get("invoiceDownloadUrl"));
        assertEquals("invoice-21.pdf", bill.get("invoiceFileName"));
    }

    @Test
    void publicMobileUserCannotOpenBillCenter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new BillController(jdbcTemplate).mobileBills(publicPrincipal(), null)
        );

        assertEquals(403, exception.getCode());
    }

    @Test
    void tenantCannotReadAnotherTenantsBills() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM mobile_user mobile"), eq(7L)))
                .thenReturn(List.of(tenantManagerIdentity()));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);

        new BillController(jdbcTemplate).mobileBills(tenantPrincipal(), null);

        verify(jdbcTemplate).query(
                argThat((String sql) -> sql != null
                        && sql.contains("FROM bill b")
                        && sql.contains("b.tenant_id = ?")),
                any(RowMapper.class),
                eq(8L),
                eq(20),
                eq(0)
        );
    }

    @Test
    void mobileBillFiltersAreAppliedBeforePagination() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM mobile_user mobile"), eq(7L)))
                .thenReturn(List.of(tenantManagerIdentity()));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(Map.of());

        new BillController(jdbcTemplate)
                .mobileBills(tenantPrincipal(), "OVERDUE", null, "energy", 1, 20);

        verify(jdbcTemplate).query(
                argThat((String sql) -> sql != null
                        && sql.contains("b.due_date < CURRENT_DATE")
                        && sql.contains("b.source_type = 'ENERGY'")
                        && sql.contains("payment_record.paid_at AS paidAt")
                        && sql.contains("THEN 0")),
                any(RowMapper.class),
                eq(8L),
                eq(20),
                eq(0)
        );
    }

    @Test
    void mobileBillInvoiceDownloadStreamsTenantOwnedInvoiceFile() throws IOException {
        Path invoice = Files.createTempFile("invoice-21-", ".pdf");
        Files.writeString(invoice, "invoice-pdf", StandardCharsets.UTF_8);

        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "fileName", "invoice-21.pdf",
                        "storagePath", invoice.toString(),
                        "mimeType", "application/pdf"
                )));
        when(jdbcTemplate.queryForList(contains("FROM mobile_user mobile"), eq(7L)))
                .thenReturn(List.of(tenantManagerIdentity()));

        ResponseEntity<Resource> response = new BillController(jdbcTemplate)
                .mobileDownloadInvoice(tenantPrincipal(), 21L);

        assertEquals(200, response.getStatusCode().value());
        assertEquals("invoice-pdf", new String(response.getBody().getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void mobileWorkOrderCreateReturnsPayloadExpectedByMiniprogram() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(31L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "reporterName", "张三",
                "reporterPhone", "13800000000",
                "companyName", "云启科技"
        )));

        ApiResponse<Map<String, Object>> response = new WorkOrderController(jdbcTemplate)
                .mobileCreateWorkOrder(tenantPrincipal(), Map.of(
                        "reporterName", "张三",
                        "companyName", "云启科技",
                        "reportedAt", "2026-07-28 21:10:30",
                        "problemCategory", "AIR_CONDITIONER",
                        "description", "出风不冷"
                ));

        assertEquals(200, response.code());
        assertEquals(31L, response.data().get("id"));
        assertTrue(String.valueOf(response.data().get("orderNumber")).startsWith("WO-"));
    }

    @Test
    void mobileMeetingHomeAndBookingMatchMiniprogramShape() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM meeting_room r") && !sql.contains("FOR UPDATE")) {
                return List.of(Map.of("id", 1L, "roomName", "第一会议室", "available", true));
            }
            if (sql.contains("FROM meeting_room") && sql.contains("FOR UPDATE")) {
                return List.of(Map.of("id", 1L, "capacity", 12));
            }
            if (sql.contains("contact_record.name AS contactName")) {
                return List.of(Map.of("contactName", "张三", "contactPhone", "13800000000"));
            }
            return List.of();
        });
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenAnswer(invocation -> ((String) invocation.getArgument(0)).contains(
                        "SELECT id FROM meeting_booking WHERE code"
                ) ? 41L : 0L);

        MeetingController controller = new MeetingController(jdbcTemplate);
        ApiResponse<Map<String, Object>> home = controller.mobileMeetings(tenantPrincipal(), "2026-05-28T10:00:00", "2026-05-28T11:00:00");
        ApiResponse<Map<String, Object>> created = controller.mobileCreateBooking(tenantPrincipal(), Map.of(
                "meetingRoomId", 1L,
                "startTime", "2026-05-28T10:00:00",
                "endTime", "2026-05-28T11:00:00",
                "purpose", "项目会议",
                "departmentName", "产品部",
                "participantCount", 6,
                "remark", "需要提前开门"
        ));

        assertEquals(200, home.code());
        assertTrue(home.data().containsKey("rooms"));
        assertTrue(home.data().containsKey("bookings"));
        assertEquals(41L, created.data().get("id"));
        assertTrue(String.valueOf(created.data().get("bookingNumber")).startsWith("MBK-"));
        assertEquals("PENDING", created.data().get("status"));
    }

    @Test
    void publicMobileUserCannotOpenOrCreateMeetingBooking() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        MeetingController controller = new MeetingController(jdbcTemplate);
        BusinessException listException = assertThrows(
                BusinessException.class,
                () -> controller.mobileMeetings(
                        publicPrincipal(),
                        "2026-05-28T10:00:00",
                        "2026-05-28T11:00:00"
                )
        );
        BusinessException createException = assertThrows(
                BusinessException.class,
                () -> controller.mobileCreateBooking(publicPrincipal(), Map.of(
                        "meetingRoomId", 1L,
                        "startTime", "2026-05-28T10:00:00",
                        "endTime", "2026-05-28T11:00:00",
                        "purpose", "项目会议"
                ))
        );

        assertEquals(403, listException.getCode());
        assertEquals(403, createException.getCode());
    }

    @Test
    void mobileVisitorAndCheckinWritesReturnCreatedRecords() {
        JdbcTemplate visitorJdbc = mock(JdbcTemplate.class);
        when(visitorJdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(51L);
        ApiResponse<Map<String, Object>> visitor = new VisitorController(visitorJdbc)
                .mobileCreate(tenantPrincipal(), Map.of(
                        "visitorName", "张三",
                        "visitorPhone", "13800000000",
                        "visitTime", "2026-05-28T10:00:00",
                        "visitReason", "拜访"
                ));

        JdbcTemplate checkinJdbc = mock(JdbcTemplate.class);
        Map<String, Object> staffIdentity = staffIdentity(
                "13800001234",
                LocalDateTime.of(2026, 7, 30, 10, 0),
                "WECHAT_API",
                "13800001234"
        );
        when(checkinJdbc.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(staffIdentity));
        when(checkinJdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(61L);
        ApiResponse<Map<String, Object>> checkin = new CheckinController(checkinJdbc)
                .mobileCreateCheckin(staffPrincipal(), Map.of("checkinType", "ON_DUTY", "location", "园区"));

        assertEquals(51L, visitor.data().get("id"));
        assertEquals(61L, checkin.data().get("id"));
        assertTrue(checkin.data().containsKey("checkinTime"));
    }

    @Test
    void publicMobileUserCannotOpenOrCreateVisitorAppointment() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VisitorController controller = new VisitorController(jdbcTemplate);
        BusinessException listException = assertThrows(
                BusinessException.class,
                () -> controller.mobileVisitors(publicPrincipal())
        );
        BusinessException createException = assertThrows(
                BusinessException.class,
                () -> controller.mobileCreate(publicPrincipal(), Map.of(
                        "visitorName", "张三",
                        "visitorPhone", "13800000000",
                        "visitTime", "2026-05-28T10:00:00",
                        "visitReason", "拜访"
                ))
        );

        assertEquals(403, listException.getCode());
        assertEquals(403, createException.getCode());
    }

    @Test
    void staffCannotUseTenantVisitorListToReadUnmaskedPhones() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VisitorController controller = new VisitorController(jdbcTemplate);
        AuthPrincipal staffWithVisitorRead = new AuthPrincipal(
                9L,
                "mobile:9",
                "STAFF",
                null,
                List.of("MOBILE_STAFF"),
                List.of("visitor.staff.view")
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> controller.mobileVisitors(staffWithVisitorRead)
        );

        assertEquals(403, exception.getCode());
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void anonymousUserCannotOpenOrCreateVisitorAppointment() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        VisitorController controller = new VisitorController(jdbcTemplate);
        BusinessException listException = assertThrows(
                BusinessException.class,
                () -> controller.mobileVisitors(null)
        );
        BusinessException createException = assertThrows(
                BusinessException.class,
                () -> controller.mobileCreate(null, Map.of(
                        "visitorName", "张三",
                        "visitorPhone", "13800000000",
                        "visitTime", "2026-05-28T10:00:00",
                        "visitReason", "拜访"
                ))
        );

        assertEquals(403, listException.getCode());
        assertEquals(403, createException.getCode());
    }

    @Test
    void mobileAnnouncementDetailReturnsOnePublishedNotice() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", 1L,
                        "title", "园区公告",
                        "content", "公告内容",
                        "type", "NOTICE",
                        "publishTime", "2026-05-27 10:00:00"
                )));

        ApiResponse<Map<String, Object>> response = new NoticeController(jdbcTemplate)
                .mobileDetail(1L);

        assertEquals(200, response.code());
        assertEquals("园区公告", response.data().get("title"));
        assertEquals("NOTICE", response.data().get("type"));
    }

    private AuthPrincipal tenantPrincipal() {
        return new AuthPrincipal(
                7L,
                "mobile:7",
                "TENANT",
                8L,
                List.of("MOBILE_USER"),
                List.of(
                        "announcement.view",
                        "investment.view",
                        "investment.booking.create",
                        "bill.view",
                        "contract.view",
                        "workorder.create",
                        "workorder.view",
                        "meeting.book",
                        "meeting.mine.view",
                        "visitor.create",
                        "parking.mine.view"
                )
        );
    }

    private Map<String, Object> tenantManagerIdentity() {
        return tenantIdentity(
                "13800001234",
                LocalDateTime.of(2026, 7, 30, 10, 0),
                "13800001234"
        );
    }

    private AuthPrincipal principalFor(Map<String, Object> identity) {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                argThat((String sql) -> sql != null && sql.contains("FROM mobile_user mobile")),
                any(Object[].class)
        )).thenReturn(List.of(identity));
        return MobileApiSupport.currentPrincipal(jdbcTemplate, 53L).orElseThrow();
    }

    private Map<String, Object> staffIdentity(
            String phone,
            LocalDateTime phoneVerifiedAt,
            String phoneVerificationSource,
            String staffPhone) {
        Map<String, Object> identity = new HashMap<>();
        identity.put("mobileUserId", 53L);
        identity.put("phone", phone);
        identity.put("phoneVerifiedAt", phoneVerifiedAt);
        identity.put("phoneVerificationSource", phoneVerificationSource);
        identity.put("declaredUserType", "INTERNAL");
        identity.put("currentBindType", "INTERNAL");
        identity.put("boundSysUserId", 1L);
        identity.put("staffPhone", staffPhone);
        identity.put("staffStatus", "ENABLED");
        identity.put("mobileEnabled", 1);
        identity.put("hasMobileWorkbenchAccess", 1);
        return identity;
    }

    private Map<String, Object> tenantIdentity(
            String phone,
            LocalDateTime phoneVerifiedAt,
            String contactPhone) {
        Map<String, Object> identity = new HashMap<>();
        identity.put("mobileUserId", 7L);
        identity.put("phone", phone);
        identity.put("phoneVerifiedAt", phoneVerifiedAt);
        identity.put("declaredUserType", "TENANT");
        identity.put("currentBindType", "TENANT");
        identity.put("tenantId", 8L);
        identity.put("tenantContactId", 18L);
        identity.put("contactRole", "RESPONSIBLE");
        identity.put("isPrimaryContact", 1);
        identity.put("tenantStatus", "ACTIVE");
        identity.put("contactStatus", "ACTIVE");
        identity.put("contactPhone", contactPhone);
        return identity;
    }

    private AuthPrincipal publicPrincipal() {
        return new AuthPrincipal(11L, "mobile:11", "PUBLIC", null, List.of("MOBILE_USER"), List.of("mobile:view"));
    }

    private AuthPrincipal staffPrincipal() {
        return new AuthPrincipal(
                9L,
                "mobile:9",
                "STAFF",
                null,
                List.of("MOBILE_STAFF"),
                List.of("announcement.view", "investment.view", "investment.booking.create")
        );
    }
}
