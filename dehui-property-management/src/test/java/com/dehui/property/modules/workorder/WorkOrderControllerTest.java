package com.dehui.property.modules.workorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.workorder.controller.WorkOrderController;
import com.dehui.property.security.AuthPrincipal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class WorkOrderControllerTest {

    private static final AuthPrincipal ADMIN = new AuthPrincipal(
            1L, "admin", "INTERNAL", null, List.of("ADMIN"), List.of("workorder:view", "workorder:assign"));
    private static final AuthPrincipal ENGINEER = new AuthPrincipal(
            8L, "engineer", "INTERNAL", null, List.of("ENGINEER"), List.of("workorder:view"));
    private static final AuthPrincipal PROPERTY_MANAGER = new AuthPrincipal(
            9L, "manager", "INTERNAL", null, List.of("PROPERTY_MANAGER"), List.of("workorder:view"));

    @Test
    void listSupportsFiltersAndPagination() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(18L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 1L, "orderNumber", "WO-001", "status", "SUBMITTED")
        ));

        ApiResponse<PageResponse<Map<String, Object>>> response = new WorkOrderController(jdbcTemplate)
                .workorders(ADMIN, "空调", "REPAIR", null, "云启", 1L, null, "SUBMITTED", "2026-05-01", "2026-05-31", 2, 10);

        assertEquals(200, response.code());
        assertEquals(18L, response.data().total());
        assertEquals(2, response.data().page());
        assertEquals(10, response.data().pageSize());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        String sql = sqlCaptor.getValue();
        assertTrue(sql.contains("w.code LIKE ?"));
        assertTrue(sql.contains("w.category = ?"));
        assertTrue(sql.contains("t.name LIKE ?"));
        assertTrue(sql.contains("w.assignee_id = ?"));
        assertTrue(sql.contains("w.work_status = ?"));
        assertTrue(sql.contains("w.created_at >= ?"));
        assertTrue(sql.contains("w.created_at <= ?"));
        assertTrue(sql.contains("AS reporterName"));
        assertTrue(sql.contains("AS reporterPhone"));
        assertTrue(sql.contains("LEFT JOIN mobile_user"));
        assertTrue(sql.contains("LEFT JOIN tenant_contact"));
        assertTrue(sql.contains("LIMIT ? OFFSET ?"));
        assertFalse(sql.contains("r.room_no, w.remark"));
    }

    @Test
    void listUsesWhitelistedWorkspaceOrdering() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        new WorkOrderController(jdbcTemplate).workorders(
                ADMIN,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "priority:desc,title:asc,unknown:asc",
                "status:asc",
                1,
                20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY w.work_status ASC, w.priority DESC, w.title ASC, w.id DESC"));
        assertTrue(!sqlCaptor.getValue().contains("unknown"));
    }

    @Test
    void mobileCreateStoresEditableSnapshotsButKeepsVerifiedPhoneAndTenantScope() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(21L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "reporterName", "张三",
                "reporterPhone", "13800000000",
                "companyName", "默认公司"
        )));

        new WorkOrderController(jdbcTemplate)
                .mobileCreateWorkOrder(
                        new AuthPrincipal(15L, "mobile", "TENANT", 7L, List.of("TENANT"), List.of()),
                        Map.of(
                                "reporterName", "李经理",
                                "companyName", "项目公司",
                                "reportedAt", "2026-07-28 21:10:30",
                                "problemCategory", "AIR_CONDITIONER",
                                "description", "空调不制冷",
                                "remark", "下午方便上门",
                                "contactPhone", "13999999999"
                        )
                );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());
        String insertSql = sqlCaptor.getAllValues().get(0);
        Object[] insertArgs = argsCaptor.getAllValues().get(0);

        assertTrue(insertSql.contains("reporter_name"));
        assertTrue(insertSql.contains("reporter_phone"));
        assertTrue(insertSql.contains("reported_company_name"));
        assertTrue(insertSql.contains("reported_at"));
        assertTrue(Arrays.asList(insertArgs).contains("李经理"));
        assertTrue(Arrays.asList(insertArgs).contains("项目公司"));
        assertTrue(Arrays.asList(insertArgs).contains("13800000000"));
        assertTrue(Arrays.asList(insertArgs).contains(Timestamp.valueOf("2026-07-28 21:10:30")));
        assertTrue(!Arrays.asList(insertArgs).contains("13999999999"));
    }

    @Test
    void backendCreateStoresCurrentUserAsReporter() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("reporterName", "管理员", "reporterPhone", "13900000000")
        ));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        new WorkOrderController(jdbcTemplate)
                .create(ADMIN, Map.of("title", "后台报修", "category", "REPAIR"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), argsCaptor.capture());

        assertTrue(sqlCaptor.getValue().contains("reporter_name"));
        assertTrue(sqlCaptor.getValue().contains("reporter_phone"));
        assertTrue(Arrays.asList(argsCaptor.getValue()).contains("管理员"));
        assertTrue(Arrays.asList(argsCaptor.getValue()).contains("13900000000"));
    }

    @Test
    void engineerOnlySeesAssignedWorkOrders() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        new WorkOrderController(jdbcTemplate)
                .workorders(ENGINEER, null, null, null, null, null, null, null, null, null, 1, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), argsCaptor.capture());
        assertTrue(sqlCaptor.getValue().contains("w.assignee_id = ?"));
        assertEquals(ENGINEER.userId(), argsCaptor.getValue()[0]);
    }

    @Test
    void controllerDoesNotRejectAConfiguredBusinessRoleByRoleCode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        ApiResponse<PageResponse<Map<String, Object>>> response = new WorkOrderController(jdbcTemplate)
                .workorders(PROPERTY_MANAGER, null, null, null, null, null, null, null, null, null, 1, 20);

        assertEquals(200, response.code());
    }

    @Test
    void adminCanAdjustStatusAndReassignHandler() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        new WorkOrderController(jdbcTemplate)
                .changeStatus(ADMIN, 12L, Map.of("status", "ASSIGNED", "handlerId", 88L, "remark", "重新派单"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), any(Object[].class));
        assertTrue(sqlCaptor.getAllValues().stream().anyMatch(sql -> sql.contains("assignee_id = ?")));
    }

    @Test
    void backendAssignCreatesUnreadMobileNotificationForHandler() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                Map.of("orderNumber", "WO-001", "title", "空调维修", "location", "A-101")
        ));

        new WorkOrderController(jdbcTemplate)
                .assign(ADMIN, 12L, Map.of("handlerId", 88L));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());

        assertTrue(sqlCaptor.getAllValues().stream().anyMatch(sql -> sql.contains("INSERT INTO work_order_mobile_notification")));
        assertTrue(argsCaptor.getAllValues().stream().anyMatch(args -> Arrays.asList(args).contains(88L)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailReturnsTenantFilesAndPropertyHandlingRecordsSeparately() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(contains("FROM work_order w"), any(Object[].class))).thenReturn(List.of(
                new java.util.LinkedHashMap<>(Map.of(
                        "id", 12L,
                        "code", "WO-012",
                        "title", "电路报修",
                        "status", "CLOSED"
                ))
        ));
        when(jdbcTemplate.queryForList(
                contains("a.work_order_record_id IS NULL"),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "fileId", 36L,
                "name", "tenant.jpg",
                "url", "/api/files/36"
        )));
        when(jdbcTemplate.queryForList(
                contains("FROM work_order_record record"),
                any(Object[].class)
        )).thenReturn(List.of(new java.util.LinkedHashMap<>(Map.of(
                "id", 21L,
                "action", "PROGRESS",
                "content", "已解决",
                "operatorName", "维修员工",
                "createdTime", Timestamp.valueOf("2026-07-30 14:07:14")
        ))));
        when(jdbcTemplate.queryForList(
                contains("attachment.work_order_record_id = ?"),
                any(Object[].class)
        )).thenReturn(List.of(Map.of(
                "fileId", 38L,
                "name", "repair.png",
                "url", "/api/files/38"
        )));
        when(jdbcTemplate.queryForList(
                contains("FROM custom_field_value value_record"),
                any(Object[].class)
        )).thenReturn(List.of(Map.of("fieldKey", "custom_repair_photo")));

        Map<String, Object> detail = new WorkOrderController(jdbcTemplate).detail(ADMIN, 12L).data();
        List<Map<String, Object>> records = (List<Map<String, Object>>) detail.get("records");

        assertEquals("tenant.jpg", ((List<Map<String, Object>>) ((Map<String, Object>) detail.get("images")).get("files")).getFirst().get("name"));
        assertEquals("维修员工", records.getFirst().get("operatorName"));
        assertEquals("repair.png", ((List<Map<String, Object>>) records.getFirst().get("attachments")).getFirst().get("name"));
        assertEquals(List.of("custom_repair_photo"), detail.get("propertyCustomFieldKeys"));
    }

    @Test
    void backendCompleteStoresTheSubmittedHandlingResultOnTheCompletedRecord() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        new WorkOrderController(jdbcTemplate).complete(
                ADMIN,
                12L,
                Map.of("handlingResult", "更换故障线路并复测正常")
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate, atLeastOnce()).update(sqlCaptor.capture(), argsCaptor.capture());

        assertTrue(argsCaptor.getAllValues().stream()
                .anyMatch(args -> Arrays.asList(args).contains("更换故障线路并复测正常")));
        assertTrue(sqlCaptor.getAllValues().stream()
                .anyMatch(sql -> sql.contains("INSERT INTO work_order_record")));
    }
}
