package com.dehui.property.modules.investment;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.investment.controller.InvestmentController;
import com.dehui.property.security.AuthPrincipal;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

class InvestmentControllerTest {

    @TempDir
    Path tempDir;

    @Test
    void adminListingStatusUsesActiveContractWhilePreservingPhysicalOccupancy() throws Exception {
        InvestmentController controller = new InvestmentController(mock(JdbcTemplate.class));
        Method method = InvestmentController.class.getDeclaredMethod("listingSelectSql");
        method.setAccessible(true);

        String sql = (String) method.invoke(controller);

        assertTrue(sql.contains("THEN 'RENTED'"));
        assertTrue(sql.contains("active_contract.status = 'ACTIVE'"));
        assertTrue(sql.contains("active_cr.room_id = r.id"));
        assertTrue(sql.contains("active_contract.room_id = r.id"));
        assertTrue(sql.contains("r.rent_status AS occupancyStatus"));
    }

    @Test
    void createListingRejectsDirectPublishedStatus() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(88L))).thenReturn(1);
        InvestmentController controller = new InvestmentController(jdbcTemplate);

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.createListing(Map.of(
                "roomId", 88L,
                "title", "3F office listing",
                "publishStatus", "PUBLISHED"
        )));

        assertEquals(400, exception.getCode());
        assertEquals("请先保存草稿并上传图片后再上架", exception.getMessage());
        verify(jdbcTemplate, never()).update(argThat((String sql) -> sql != null && sql.contains("INSERT INTO investment_room_listing")), any(Object[].class));
    }

    @Test
    void publishListingRequiresAnAvailableRoomImageAndUniquePublishedListing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.startsWith("SELECT id, room_id, title")), eq(15L)))
                .thenReturn(List.of(row("id", 15L, "room_id", 88L, "title", "3F office listing", "cover_image_url", "")));
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("SELECT rent_status AS status")), eq(88L)))
                .thenReturn(List.of(row("status", "VACANT")));
        when(jdbcTemplate.queryForObject(argThat((String sql) -> sql != null && sql.contains("investment_room_image")), eq(Integer.class), eq(15L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(argThat((String sql) -> sql != null && sql.contains("id <> ?")), eq(Integer.class), eq(88L), eq(15L)))
                .thenReturn(0);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        ApiResponse<Void> response = new InvestmentController(jdbcTemplate).publishListing(15L);

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                "UPDATE investment_room_listing SET publish_status = 'PUBLISHED', updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                15L
        );
    }

    @Test
    void publishListingRejectsRentedRoom() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), eq(15L)))
                .thenReturn(List.of(row("id", 15L, "room_id", 88L, "title", "3F office listing", "cover_image_url", "/cover.jpg")));
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("SELECT rent_status AS status")), eq(88L)))
                .thenReturn(List.of(row("status", "RENTED")));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new InvestmentController(jdbcTemplate).publishListing(15L)
        );

        assertEquals(409, exception.getCode());
        assertEquals("关联房间当前状态不允许公开招商", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void publishListingRejectsMissingImage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.startsWith("SELECT id, room_id, title")), eq(15L)))
                .thenReturn(List.of(row("id", 15L, "room_id", 88L, "title", "3F office listing", "cover_image_url", "")));
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("SELECT rent_status AS status")), eq(88L)))
                .thenReturn(List.of(row("status", "AVAILABLE")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(15L))).thenReturn(0);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new InvestmentController(jdbcTemplate).publishListing(15L)
        );

        assertEquals(400, exception.getCode());
        assertEquals("上架前请至少上传一张房源图片", exception.getMessage());
    }

    @Test
    void publishListingRejectsAnotherPublishedListingForTheSameRoom() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.startsWith("SELECT id, room_id, title")), eq(15L)))
                .thenReturn(List.of(row("id", 15L, "room_id", 88L, "title", "3F office listing", "cover_image_url", "/cover.jpg")));
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("SELECT rent_status AS status")), eq(88L)))
                .thenReturn(List.of(row("status", "VACANT")));
        when(jdbcTemplate.queryForObject(argThat((String sql) -> sql != null && sql.contains("investment_room_image")), eq(Integer.class), eq(15L)))
                .thenReturn(1);
        when(jdbcTemplate.queryForObject(argThat((String sql) -> sql != null && sql.contains("id <> ?")), eq(Integer.class), eq(88L), eq(15L)))
                .thenReturn(1);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new InvestmentController(jdbcTemplate).publishListing(15L)
        );

        assertEquals(409, exception.getCode());
        assertEquals("该房间已有公开招商房源，请先下架原房源", exception.getMessage());
        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void adminContentListSelectsMiniProgramSectionFields() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(row(
                "id", 1L,
                "code", "INV001",
                "sectionKey", "hero",
                "sectionName", "顶部主视觉",
                "title", "招商中心",
                "subtitle", "优质办公空间",
                "content", "近地铁 / 配套完善",
                "imageUrl", "",
                "publishStatus", "PUBLISHED",
                "sortOrder", 10,
                "status", "ACTIVE",
                "updatedAt", "2026-05-26 10:00:00"
        )));

        ApiResponse<PageResponse<Map<String, Object>>> response = new InvestmentController(jdbcTemplate)
                .displays(1, 20);

        Map<String, Object> record = response.data().records().getFirst();
        assertEquals("hero", record.get("sectionKey"));
        assertEquals("顶部主视觉", record.get("sectionName"));
        assertEquals("优质办公空间", record.get("subtitle"));
        assertEquals("", record.get("imageUrl"));
        assertEquals("2026-05-26 10:00:00", record.get("updatedAt"));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sql.capture(), any(RowMapper.class), eq(20), eq(0));
        assertTrue(sql.getValue().contains("section_key AS sectionKey"));
        assertTrue(sql.getValue().contains("subtitle"));
        assertTrue(sql.getValue().contains("image_url AS imageUrl"));
        assertTrue(sql.getValue().contains("updated_at AS updatedAt"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileOverviewReturnsPublishedContentGroupedBySection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of(
                row(
                        "sectionKey", "hero",
                        "title", "招商中心",
                        "subtitle", "德汇创新中心",
                        "content", "近地铁 / 配套完善",
                        "imageUrl", "",
                        "sortOrder", 10
                ),
                row(
                        "sectionKey", "highlight",
                        "title", "近地铁交通便利",
                        "subtitle", "",
                        "content", "交通便利，周边配套完善",
                        "imageUrl", "",
                        "sortOrder", 20
                )
        ));

        ApiResponse<Map<String, Object>> response = new InvestmentController(jdbcTemplate).mobileOverview();

        Map<String, Object> data = response.data();
        assertTrue(data.containsKey("hero"));
        assertTrue(data.containsKey("highlight"));
        assertTrue(data.containsKey("policy"));
        assertTrue(data.containsKey("introduction"));
        assertTrue(data.containsKey("location"));
        assertTrue(data.containsKey("contact"));
        assertFalse(data.containsKey("notice"));
        assertFalse(data.containsKey("contents"));

        List<Map<String, Object>> hero = (List<Map<String, Object>>) data.get("hero");
        List<Map<String, Object>> highlight = (List<Map<String, Object>>) data.get("highlight");
        assertEquals("招商中心", hero.getFirst().get("title"));
        assertEquals("近地铁交通便利", highlight.getFirst().get("title"));

        verify(jdbcTemplate).queryForList(argThat(sql -> sql.contains("publish_status = 'PUBLISHED'")
                && sql.contains("section_key AS sectionKey")
                && sql.contains("ORDER BY section_key ASC, sort_order ASC")));
    }

    @Test
    void disableContentMarksContentDisabled() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(
                "UPDATE investment_display SET publish_status = 'DISABLED', updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                8L
        )).thenReturn(1);

        InvestmentController controller = new InvestmentController(jdbcTemplate);
        Method method = InvestmentController.class.getDeclaredMethod("disableContent", Long.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        method.invoke(controller, 8L);

        assertArrayEquals(new String[] {"/investment/contents/{id}/disable"}, mapping.value());
        verify(jdbcTemplate).update(
                "UPDATE investment_display SET publish_status = 'DISABLED', updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                8L
        );
    }

    @Test
    void deleteContentSoftDeletesContent() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(
                "UPDATE investment_display SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                9L
        )).thenReturn(1);

        InvestmentController controller = new InvestmentController(jdbcTemplate);
        Method method = InvestmentController.class.getDeclaredMethod("deleteContent", Long.class);
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);

        method.invoke(controller, 9L);

        assertArrayEquals(new String[] {"/investment/contents/{id}"}, mapping.value());
        verify(jdbcTemplate).update(
                "UPDATE investment_display SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                9L
        );
    }

    @Test
    void deleteLeadSoftDeletesLead() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT id, lead_status FROM investment_lead WHERE id = ? AND deleted = 0",
                12L
        )).thenReturn(List.of(row("id", 12L, "lead_status", "INVALID")));
        when(jdbcTemplate.update(
                "UPDATE investment_lead SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                12L
        )).thenReturn(1);

        InvestmentController controller = new InvestmentController(jdbcTemplate);
        Method method = InvestmentController.class.getDeclaredMethod("deleteLead", Long.class);
        DeleteMapping mapping = method.getAnnotation(DeleteMapping.class);

        method.invoke(controller, 12L);

        assertArrayEquals(new String[] {"/investment/leads/{id}"}, mapping.value());
        verify(jdbcTemplate).update(
                "UPDATE investment_lead SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                12L
        );
    }

    @Test
    void publicLeadRemainsOwnedByTheSameMobileUserAfterTenantUpgrade() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuthPrincipal publicPrincipal = new AuthPrincipal(21L, "mobile:21", "PUBLIC", null, List.of("MOBILE_USER"), List.of("investment.booking.create"));
        AuthPrincipal upgradedTenantPrincipal = new AuthPrincipal(21L, "mobile:21", "TENANT", 8L, List.of("MOBILE_USER", "MOBILE_TENANT"), List.of("investment.booking.create"));
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("FROM mobile_user")), eq(21L)))
                .thenReturn(List.of(row("id", 21L, "phone", "13800000000", "profileName", "张三")));
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null
                && sql.contains("FROM investment_room_listing l")
                && sql.contains("WHERE l.id = ?")), eq(15L)))
                .thenReturn(List.of(row(
                        "id", 15L,
                        "listingId", 15L,
                        "roomId", 88L,
                        "title", "A座 1201",
                        "buildingName", "A座",
                        "floorName", "12F",
                        "roomNumber", "1201",
                        "area", "120.00",
                        "displayAreaSnapshot", "120.00",
                        "availableForAppointment", 1
                )));
        when(jdbcTemplate.queryForObject(argThat((String sql) -> sql != null
                && sql.contains("created_at >= DATE_SUB")), eq(Integer.class), eq(21L), eq(15L)))
                .thenReturn(0);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("FROM investment_lead")
                && sql.contains("mobile_user_id = ?")), any(Object[].class)))
                .thenReturn(List.of(row(
                        "id", 8L,
                        "code", "LEAD-001",
                        "name", "张三",
                        "phone", "13800000000",
                        "companyName", "测试公司",
                        "desiredArea", "120.00",
                        "intendedUse", "研发办公",
                        "preferredVisitTime", "2026-05-30 10:00",
                        "status", "NEW",
                        "remark", "需要朝南"
                )));

        InvestmentController controller = new InvestmentController(jdbcTemplate);
        controller.mobileLead(publicPrincipal, Map.ofEntries(
                Map.entry("name", "张三"),
                Map.entry("companyName", "测试公司"),
                Map.entry("listingId", 15L),
                Map.entry("desiredArea", 120),
                Map.entry("intendedUse", "研发办公"),
                Map.entry("preferredVisitTime", "2026-05-30 10:00"),
                Map.entry("remark", "需要朝南"),
                Map.entry("mobileUserId", 999L),
                Map.entry("openid", "forged-openid"),
                Map.entry("tenantId", 999L),
                Map.entry("roomId", 999L),
                Map.entry("phone", "13999999999")
        ));

        ApiResponse<List<Map<String, Object>>> response = controller.mobileMyLeads(upgradedTenantPrincipal);
        Map<String, Object> first = response.data().getFirst();
        assertEquals("LEAD-001", first.get("code"));
        assertEquals("2026-05-30 10:00", first.get("preferredVisitTime"));

        ArgumentCaptor<Object[]> insertArguments = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(argThat((String sql) -> sql != null
                        && sql.contains("INSERT INTO investment_lead")
                        && sql.contains("mobile_user_id")
                        && sql.contains("listing_title_snapshot")
                        && sql.contains("intended_room")
                        && sql.contains("room_id")
                        && sql.contains("listing_id")
                        && sql.contains("preferred_visit_time")
                        && sql.contains("created_by")),
                insertArguments.capture());
        assertEquals("13800000000", insertArguments.getValue()[2]);
        assertEquals(88L, insertArguments.getValue()[5]);
        assertEquals(21L, insertArguments.getValue()[7]);
        assertFalse(List.of(insertArguments.getValue()).contains("forged-openid"));
        assertFalse(List.of(insertArguments.getValue()).contains(999L));
    }

    @Test
    void mobileAppointmentsAreIsolatedByCurrentMobileUserAndAnonymousAccessIsRejected() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        AuthPrincipal userA = new AuthPrincipal(21L, "mobile:21", "PUBLIC", null, List.of("MOBILE_USER"), List.of("investment.booking.create"));
        AuthPrincipal userB = new AuthPrincipal(22L, "mobile:22", "PUBLIC", null, List.of("MOBILE_USER"), List.of("investment.booking.create"));
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null
                && sql.contains("FROM investment_lead")
                && sql.contains("mobile_user_id = ?")), any(Object[].class)))
                .thenAnswer(invocation -> {
                    Long currentMobileUserId = invocation.getArgument(1, Long.class);
                    if (Long.valueOf(21L).equals(currentMobileUserId)) {
                        return List.of(row("id", 8L, "code", "LEAD-A", "name", "User A", "status", "NEW"));
                    }
                    return List.of();
                });

        InvestmentController controller = new InvestmentController(jdbcTemplate);

        assertEquals("LEAD-A", controller.mobileMyLeads(userA).data().getFirst().get("code"));
        assertTrue(controller.mobileMyLeads(userB).data().isEmpty());
        BusinessException otherUsersDetail = assertThrows(
                BusinessException.class,
                () -> controller.mobileAppointmentDetail(8L, userB)
        );
        assertEquals(404, otherUsersDetail.getCode());
        BusinessException anonymous = assertThrows(
                BusinessException.class,
                () -> controller.mobileMyLeads(null)
        );
        assertEquals(401, anonymous.getCode());
    }

    @Test
    void mobileLeadRejectsInvalidViewingAppointmentFieldsBeforeInsert() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        InvestmentController controller = new InvestmentController(jdbcTemplate);
        AuthPrincipal principal = new AuthPrincipal(21L, "mobile:21", "PUBLIC", null, List.of("MOBILE_USER"), List.of("mobile:view"));

        assertValidationMessage(controller, principal, Map.of(
                "name", "123",
                "phone", "13800000000",
                "companyName", "德汇科技有限公司"
        ), "请输入正确的联系人姓名");
        assertValidationMessage(controller, principal, Map.of(
                "name", "张三",
                "phone", "13800000000",
                "companyName", "12312"
        ), "请输入正确的公司名称");
        assertValidationMessage(controller, principal, Map.of(
                "name", "张三",
                "phone", "13800000000",
                "companyName", "德汇科技有限公司",
                "desiredArea", "abc"
        ), "请输入正确的意向面积，只能填写数字");
        assertValidationMessage(controller, principal, Map.of(
                "name", "张三",
                "phone", "13800000000",
                "companyName", "德汇科技有限公司",
                "remark", "<script>alert(1)</script>"
        ), "备注最多 200 个字符，不能包含特殊脚本内容");

        verify(jdbcTemplate, never()).update(argThat((String sql) -> sql != null && sql.contains("INSERT INTO investment_lead")), any(Object[].class));
    }

    @Test
    void mobileInvestmentRoomsReturnsPublishedListingsForViewingBoard() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null
                && sql.contains("FROM investment_room_listing l")
                && sql.contains("l.publish_status = 'PUBLISHED'")
                && sql.contains("AND NOT")
                && sql.contains("active_contract.status = 'ACTIVE'")), any(Object[].class)))
                .thenReturn(List.of(row(
                        "id", 15L,
                        "listingId", 15L,
                        "roomId", 88L,
                        "title", "3F office listing",
                        "buildingName", "德汇创新中心",
                        "floorName", "3F",
                        "roomNumber", "3F308",
                        "area", "120.00",
                        "roomType", "OFFICE",
                        "status", "VACANT",
                        "description", "采光好"
                )));

        ApiResponse<List<Map<String, Object>>> response = new InvestmentController(jdbcTemplate).mobileRooms();

        Map<String, Object> first = response.data().getFirst();
        assertEquals(15L, first.get("id"));
        assertEquals(15L, first.get("listingId"));
        assertFalse(first.containsKey("roomId"));
        assertEquals("3F office listing", first.get("title"));
        assertEquals("德汇创新中心", first.get("buildingName"));
        assertEquals("3F", first.get("floorName"));
        assertEquals("3F308", first.get("roomNumber"));
        assertEquals("VACANT", first.get("status"));

        verify(jdbcTemplate).queryForList(argThat((String sql) -> sql != null
                && sql.contains("investment_room_listing l")
                && sql.contains("LEFT JOIN building b")
                && sql.contains("LEFT JOIN building_floor f")
                && sql.contains("ORDER BY l.is_recommended DESC, l.sort_order ASC")), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileInvestmentRoomsIncludesImageUrlsForCards() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null
                && sql.contains("FROM investment_room_listing l")
                && sql.contains("l.publish_status = 'PUBLISHED'")), any(Object[].class)))
                .thenReturn(List.of(row(
                        "id", 15L,
                        "listingId", 15L,
                        "roomId", 88L,
                        "title", "B-101 精装办公空间",
                        "coverImageUrl", "/api/files/public/investment/rooms/cover.jpg",
                        "buildingName", "德汇创新中心B座",
                        "floorName", "1F",
                        "roomNumber", "B-101",
                        "area", "88.60",
                        "roomType", "OFFICE",
                        "status", "VACANT"
                )));
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null
                && sql.contains("FROM investment_room_image")
                && sql.contains("listing_id IN (?)")
                && sql.contains("ORDER BY listing_id ASC")), any(Object[].class)))
                .thenReturn(List.of(
                        row("id", 3L, "listingId", 15L, "roomId", 88L, "imageUrl", "/api/files/public/investment/rooms/cover.jpg", "imageType", "COVER", "sortOrder", 0),
                        row("id", 4L, "listingId", 15L, "roomId", 88L, "imageUrl", "/api/files/public/investment/rooms/gallery.jpg", "imageType", "GALLERY", "sortOrder", 1)
                ));

        ApiResponse<List<Map<String, Object>>> response = new InvestmentController(jdbcTemplate).mobileRooms();

        Map<String, Object> first = response.data().getFirst();
        List<String> imageUrls = (List<String>) first.get("imageUrls");
        assertEquals(List.of(
                "/api/files/public/investment/rooms/cover.jpg",
                "/api/files/public/investment/rooms/gallery.jpg"
        ), imageUrls);
        assertEquals(2, ((List<Map<String, Object>>) first.get("images")).size());
        verify(jdbcTemplate, times(1)).queryForList(argThat((String sql) -> sql != null
                && sql.contains("FROM investment_room_image")
                && sql.contains("listing_id IN (?)")), any(Object[].class));
    }

    @Test
    void listingImageUploadRejectsNonImageContentEvenWithImageExtension() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT id, room_id, title, cover_image_url, publish_status FROM investment_room_listing WHERE id = ? AND deleted = 0",
                15L
        )).thenReturn(List.of(row("id", 15L, "room_id", 88L, "title", "3F office listing")));
        MockMultipartFile disguisedText = new MockMultipartFile(
                "file",
                "not-an-image.jpg",
                "text/plain",
                "plain text".getBytes()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new InvestmentController(jdbcTemplate).uploadListingImage(15L, disguisedText, "COVER", 0)
        );

        assertEquals(400, exception.getCode());
        assertEquals("文件内容与允许的图片类型不匹配", exception.getMessage());
        verify(jdbcTemplate, never()).update(argThat((String sql) -> sql != null
                && sql.contains("INSERT INTO investment_room_image")), any(Object[].class));
    }

    @Test
    void listingImageUploadAcceptsImageLargerThanFiveMegabytesWithinTwentyMegabyteLimit() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT id, room_id, title, cover_image_url, publish_status FROM investment_room_listing WHERE id = ? AND deleted = 0",
                15L
        )).thenReturn(List.of(row("id", 15L, "room_id", 88L, "title", "3F office listing")));
        when(jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class)).thenReturn(31L);
        InvestmentController controller = new InvestmentController(jdbcTemplate);
        Field uploadDir = InvestmentController.class.getDeclaredField("uploadDir");
        uploadDir.setAccessible(true);
        uploadDir.set(controller, tempDir.toString());
        MockMultipartFile image = new MockMultipartFile(
                "file",
                "large-office.jpg",
                "image/jpeg",
                new byte[6 * 1024 * 1024]
        );

        ApiResponse<Map<String, Object>> response =
                controller.uploadListingImage(15L, image, "GALLERY", 0);

        assertEquals(31L, response.data().get("id"));
        try (var files = Files.list(tempDir.resolve("public/investment/rooms"))) {
            assertTrue(files.findAny().isPresent());
        }
    }

    @Test
    void listingImageUploadRejectsImageLargerThanTwentyMegabytesWithChineseMessage() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT id, room_id, title, cover_image_url, publish_status FROM investment_room_listing WHERE id = ? AND deleted = 0",
                15L
        )).thenReturn(List.of(row("id", 15L, "room_id", 88L, "title", "3F office listing")));
        MockMultipartFile oversizedImage = new MockMultipartFile(
                "file",
                "oversized-office.jpg",
                "image/jpeg",
                new byte[20 * 1024 * 1024 + 1]
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new InvestmentController(jdbcTemplate)
                        .uploadListingImage(15L, oversizedImage, "GALLERY", 0)
        );

        assertEquals(400, exception.getCode());
        assertEquals("单张图片不能超过20MB", exception.getMessage());
    }

    private static Map<String, Object> row(Object... entries) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            row.put(String.valueOf(entries[i]), entries[i + 1]);
        }
        return row;
    }

    private static void assertValidationMessage(
            InvestmentController controller,
            AuthPrincipal principal,
            Map<String, Object> body,
            String expectedMessage) {
        BusinessException exception = assertThrows(BusinessException.class, () -> controller.mobileLead(principal, body));
        assertEquals(400, exception.getCode());
        assertEquals(expectedMessage, exception.getMessage());
    }
}
