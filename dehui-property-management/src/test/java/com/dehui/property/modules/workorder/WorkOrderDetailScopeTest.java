package com.dehui.property.modules.workorder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.workorder.controller.WorkOrderController;
import com.dehui.property.security.AuthPrincipal;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class WorkOrderDetailScopeTest {

    private static final AuthPrincipal LIMITED_VIEWER = new AuthPrincipal(
            35L,
            "readonly",
            "SYSTEM",
            null,
            List.of("READ_ONLY"),
            List.of("workorder:view")
    );
    private static final AuthPrincipal ASSIGNED_WITHOUT_VIEW = new AuthPrincipal(
            35L,
            "assigned-without-view",
            "SYSTEM",
            null,
            List.of("ENGINEER"),
            List.of()
    );
    private static final AuthPrincipal GLOBAL_VIEWER = new AuthPrincipal(
            1L,
            "admin",
            "SYSTEM",
            null,
            List.of("ADMIN"),
            List.of("workorder:view", "workorder:assign")
    );
    private static final AuthPrincipal SUPER_ADMIN_WITHOUT_PERMISSION_CACHE = new AuthPrincipal(
            1L,
            "super-admin",
            "SYSTEM",
            null,
            List.of("SUPER_ADMIN"),
            List.of()
    );

    @Test
    void detailHidesAWorkOrderAssignedToAnotherUserFromALimitedViewer() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class,
                () -> scopedDetailMethod().invoke(
                        new WorkOrderController(jdbcTemplate),
                        LIMITED_VIEWER,
                        12L
                )
        );

        BusinessException exception = assertInstanceOf(BusinessException.class, thrown.getCause());
        assertEquals(404, exception.getCode());
    }

    @Test
    void detailRejectsAnAssignedUserWithoutWorkOrderViewPermission() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new WorkOrderController(jdbcTemplate).detail(ASSIGNED_WITHOUT_VIEW, 12L)
        );

        assertEquals(403, exception.getCode());
    }

    @Test
    void detailRemainsReadableForTheAssignedLimitedViewer() {
        JdbcTemplate jdbcTemplate = detailJdbcTemplate();

        ApiResponse<Map<String, Object>> response =
                new WorkOrderController(jdbcTemplate).detail(LIMITED_VIEWER, 12L);

        assertEquals(200, response.code());
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(contains("FROM work_order w"), argsCaptor.capture());
        assertEquals(12L, argsCaptor.getValue()[0]);
        assertEquals(0, argsCaptor.getValue()[1]);
        assertEquals(LIMITED_VIEWER.userId(), argsCaptor.getValue()[2]);
    }

    @Test
    @SuppressWarnings("unchecked")
    void detailRemainsReadableForAViewerWithGlobalAssignmentScope() throws Exception {
        assertGlobalDetail(GLOBAL_VIEWER);
    }

    @Test
    void detailRemainsReadableForASuperAdminWhenPermissionCacheIsEmpty() throws Exception {
        assertGlobalDetail(SUPER_ADMIN_WITHOUT_PERMISSION_CACHE);
    }

    @SuppressWarnings("unchecked")
    private void assertGlobalDetail(AuthPrincipal principal) throws Exception {
        JdbcTemplate jdbcTemplate = detailJdbcTemplate();

        ApiResponse<Map<String, Object>> response = (ApiResponse<Map<String, Object>>) scopedDetailMethod().invoke(
                new WorkOrderController(jdbcTemplate),
                principal,
                12L
        );

        assertEquals(200, response.code());
        assertEquals(12L, response.data().get("id"));
        ArgumentCaptor<Object[]> argsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).queryForList(contains("FROM work_order w"), argsCaptor.capture());
        assertEquals(1, argsCaptor.getValue()[1]);
    }

    private JdbcTemplate detailJdbcTemplate() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        when(jdbcTemplate.queryForList(contains("FROM work_order w"), any(Object[].class))).thenReturn(List.of(
                new LinkedHashMap<>(Map.of(
                        "id", 12L,
                        "code", "WO-012",
                        "title", "Electrical repair",
                        "status", "ASSIGNED"
                ))
        ));
        return jdbcTemplate;
    }

    private static Method scopedDetailMethod() {
        try {
            return WorkOrderController.class.getMethod("detail", AuthPrincipal.class, Long.class);
        } catch (NoSuchMethodException exception) {
            throw new AssertionError(
                    "GET /workorders/{id} must receive AuthPrincipal so it can enforce assignee scope",
                    exception
            );
        }
    }
}
