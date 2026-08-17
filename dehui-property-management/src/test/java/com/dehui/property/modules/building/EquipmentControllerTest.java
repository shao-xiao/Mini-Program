package com.dehui.property.modules.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.building.controller.EquipmentController;
import com.dehui.property.modules.customfield.application.CustomFieldImportService;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldQueryService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.ResponseEntity;

class EquipmentControllerTest {

    @Test
    void fullDependencyConstructorIsSelectedForRuntimeInjection() throws Exception {
        var constructor = EquipmentController.class.getDeclaredConstructor(
                JdbcTemplate.class,
                CustomFieldQueryService.class,
                CustomFieldProjectionService.class,
                CustomFieldImportService.class
        );

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    void equipmentsReturnsPagedRows() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(eq("SELECT COUNT(*) FROM equipment e WHERE e.deleted = 0"), eq(Long.class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of(
                        "id", 1L,
                        "equipmentName", "1号电梯",
                        "equipmentCode", "EQ-001",
                        "status", "NORMAL"
                )));

        ApiResponse<?> response = new EquipmentController(jdbcTemplate).equipments(null, null, null, 1, 20);

        assertEquals(200, response.code());
    }

    @Test
    void equipmentsUsesWhitelistedWorkspaceOrdering() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        new EquipmentController(jdbcTemplate).equipments(
                null,
                null,
                null,
                "equipmentName:asc,unknown:desc",
                "status:desc",
                1,
                20
        );

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sqlCaptor.getValue().contains("ORDER BY e.status DESC, e.equipment_name ASC, e.id DESC"));
        assertTrue(!sqlCaptor.getValue().contains("unknown"));
    }

    @Test
    void createEquipmentWritesToMysql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class)))
                .thenReturn(1);

        ApiResponse<Void> response = new EquipmentController(jdbcTemplate).createEquipment(Map.of(
                "equipmentName", "1号电梯",
                "equipmentCode", "EQ-001",
                "equipmentType", "ELEVATOR",
                "location", "1F大厅",
                "manufacturer", "通力",
                "model", "KONE-X",
                "installDate", "2026-05-29",
                "remark", "测试"
        ));

        assertEquals(200, response.code());
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(eq(EquipmentController.INSERT_EQUIPMENT_SQL), args.capture());
        assertEquals("1号电梯", args.getValue()[1]);
        assertEquals("EQ-001", args.getValue()[2]);
        assertEquals("PUBLIC_AREA", args.getValue()[8]);
        assertEquals("通力", args.getValue()[9]);
        assertEquals("KONE-X", args.getValue()[10]);
        assertEquals("2026-05-29", args.getValue()[12]);
        assertEquals("测试", args.getValue()[21]);
    }

    @Test
    void blankEquipmentNameIsRejected() {
        EquipmentController controller = new EquipmentController(mock(JdbcTemplate.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.createEquipment(Map.of(
                "equipmentName", "   "
        )));

        assertEquals(400, exception.getCode());
        assertEquals("Equipment name is required", exception.getMessage());
    }

    @Test
    void invalidStatusIsRejected() {
        EquipmentController controller = new EquipmentController(mock(JdbcTemplate.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.updateStatus(1L, "BROKEN"));

        assertEquals(400, exception.getCode());
        assertEquals("Equipment status is invalid", exception.getMessage());
    }

    @Test
    void downloadImportTemplateReturnsMajorEquipmentHandoverColumns() {
        ResponseEntity<byte[]> response = new EquipmentController(mock(JdbcTemplate.class)).downloadImportTemplate();

        assertEquals(200, response.getStatusCode().value());
        String csv = new String(response.getBody(), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF"));
        assertTrue(csv.contains("序号,设备/材料名称,规格型号,数量,安装位置,生产厂家,厂家联系方式,质保期,所属系统,备注,固定资产编码"));
    }

    @Test
    void importEquipmentsCreatesEquipmentAndCustomFieldValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        CustomFieldImportService customFieldImportService = mock(CustomFieldImportService.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(), List.of(Map.of("id", 88L)));
        when(jdbcTemplate.update(eq(EquipmentController.INSERT_EQUIPMENT_SQL), any(Object[].class)))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ApiResponse<Map<String, Object>> response = new EquipmentController(
                jdbcTemplate,
                customFieldImportService
        ).importEquipments(Map.of(
                "rows", List.of(Map.of(
                        "equipmentName", "排气扇EF02",
                        "model", "风量250m3/h，功率32W",
                        "quantity", "10",
                        "location", "备配电间",
                        "manufacturer", "上虞清明风机有限公司",
                        "manufacturerContact", "line1\nline2",
                        "warrantyPeriod", "2025年6月18日-2027年6月18日",
                        "systemName", "通风",
                        "remark", "",
                        "assetCode", "CZXXTF001007"
                ))
        ));

        assertEquals(200, response.code());
        assertEquals(1, response.data().get("totalCount"));
        assertEquals(1, response.data().get("insertedCount"));
        assertEquals(1, response.data().get("successCount"));
        assertEquals(0, response.data().get("updatedCount"));
        assertEquals(0, response.data().get("restoredCount"));
        assertEquals(0, response.data().get("skippedCount"));
        assertEquals(0, response.data().get("failureCount"));
        assertTrue(((List<?>) response.data().get("errors")).isEmpty());
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(eq(EquipmentController.INSERT_EQUIPMENT_SQL), args.capture());
        assertEquals("排气扇EF02", args.getValue()[1]);
        assertEquals("CZXXTF001007", args.getValue()[2]);
        assertEquals("HVAC", args.getValue()[3]);
        assertEquals("备配电间", args.getValue()[4]);
        assertEquals("PUBLIC_AREA", args.getValue()[8]);
        assertEquals("上虞清明风机有限公司", args.getValue()[9]);
        assertEquals("风量250m3/h，功率32W", args.getValue()[10]);
        assertEquals("所属系统: 通风", args.getValue()[21]);
        verify(customFieldImportService).ensureDefinitions(eq("equipment"), any());
        verify(customFieldImportService).saveImportedRow(
                eq("equipment"),
                eq(88L),
                any(),
                argThat(values -> "10".equals(values.get("custom_quantity"))
                        && "line1\nline2".equals(values.get("custom_manufacturer_contact")))
        );
    }

    @Test
    void importEquipmentsSupplementsExistingEquipmentCode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("SELECT id, deleted FROM equipment WHERE equipment_code")), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 88L, "deleted", 0)));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ApiResponse<Map<String, Object>> response = importController(jdbcTemplate).importEquipments(Map.of(
                "rows", List.of(Map.of(
                        "equipmentName", "排气扇EF02",
                        "quantity", "10",
                        "sequence", "1",
                        "manufacturerContact", "李明洋-18689442292",
                        "warrantyPeriod", "2025年6月18日-2027年6月18日",
                        "installDate", "2026-06-30",
                        "equipmentCode", "CZXXTF001007",
                        "systemName", "通风"
                ))
        ));

        assertEquals(200, response.code());
        assertEquals(1, response.data().get("totalCount"));
        assertEquals(0, response.data().get("insertedCount"));
        assertEquals(0, response.data().get("successCount"));
        assertEquals(1, response.data().get("updatedCount"));
        assertEquals(0, response.data().get("restoredCount"));
        assertEquals(0, response.data().get("skippedCount"));
        assertEquals(0, response.data().get("failureCount"));
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("install_date = COALESCE(?, install_date)")),
                eq("排气扇EF02"),
                eq("HVAC"),
                any(),
                any(),
                any(),
                eq("2026-06-30"),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(0L),
                eq(88L)
        );
    }

    @Test
    void importEquipmentsRestoresSoftDeletedEquipmentCode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("SELECT id, deleted FROM equipment WHERE equipment_code")), any(Object[].class)))
                .thenReturn(List.of(Map.of("id", 88L, "deleted", 1)));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ApiResponse<Map<String, Object>> response = importController(jdbcTemplate).importEquipments(Map.of(
                "rows", List.of(Map.of(
                        "equipmentName", "消防稳压泵",
                        "quantity", "2",
                        "sequence", "6",
                        "equipmentCode", "CZXXTF000306",
                        "systemName", "消防"
                ))
        ));

        assertEquals(200, response.code());
        assertEquals(1, response.data().get("totalCount"));
        assertEquals(0, response.data().get("insertedCount"));
        assertEquals(0, response.data().get("successCount"));
        assertEquals(0, response.data().get("updatedCount"));
        assertEquals(1, response.data().get("restoredCount"));
        assertEquals(0, response.data().get("skippedCount"));
        assertEquals(0, response.data().get("failureCount"));
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("SET deleted = 0")),
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), eq(88L)
        );
    }

    @Test
    void importEquipmentsGeneratesCodeWhenFixedAssetCodeIsBlank() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(), List.of(Map.of("id", 89L)));
        when(jdbcTemplate.update(eq(EquipmentController.INSERT_EQUIPMENT_SQL), any(Object[].class)))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        ApiResponse<Map<String, Object>> response = importController(jdbcTemplate).importEquipments(Map.of(
                "rows", List.of(Map.of(
                        "equipmentName", "无编码设备",
                        "equipmentCode", "",
                        "systemName", "机电"
                ))
        ));

        assertEquals(200, response.code());
        assertEquals(1, response.data().get("insertedCount"));
        assertEquals(0, response.data().get("skippedCount"));
        assertEquals(0, response.data().get("failureCount"));
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(eq(EquipmentController.INSERT_EQUIPMENT_SQL), args.capture());
        assertEquals("无编码设备", args.getValue()[1]);
        assertTrue(String.valueOf(args.getValue()[2]).startsWith("EQP-IMP-"));
        assertEquals("OTHER", args.getValue()[3]);
        assertEquals("PUBLIC_AREA", args.getValue()[8]);
        assertEquals("所属系统: 机电", args.getValue()[21]);
    }

    @Test
    void importEquipmentsDuplicateKeyFallbackDoesNotSkipWhenCodeCanBeFound() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("SELECT id, deleted FROM equipment WHERE equipment_code")), any(Object[].class)))
                .thenReturn(List.of(), List.of(Map.of("id", 90L, "deleted", 0)));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);
        doThrow(new DuplicateKeyException("Duplicate entry"))
                .when(jdbcTemplate)
                .update(eq(EquipmentController.INSERT_EQUIPMENT_SQL), any(Object[].class));

        ApiResponse<Map<String, Object>> response = importController(jdbcTemplate).importEquipments(Map.of(
                "rows", List.of(Map.of(
                        "equipmentName", "重复导入设备",
                        "equipmentCode", "DUP-EQ-001",
                        "所属系统", "消防"
                ))
        ));

        assertEquals(200, response.code());
        assertEquals(0, response.data().get("insertedCount"));
        assertEquals(1, response.data().get("updatedCount"));
        assertEquals(0, response.data().get("skippedCount"));
        assertEquals(0, response.data().get("failureCount"));
        verify(jdbcTemplate).update(
                argThat((String sql) -> sql != null && sql.contains("SET deleted = 0")),
                eq("重复导入设备"),
                eq("FIRE"),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(0L),
                eq(90L)
        );
    }

    @Test
    void importEquipmentsReportsFailureWhenDuplicateKeyCannotBeResolved() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(argThat((String sql) -> sql != null && sql.contains("SELECT id, deleted FROM equipment WHERE equipment_code")), any(Object[].class)))
                .thenReturn(List.of(), List.of());
        when(jdbcTemplate.update(eq(EquipmentController.INSERT_EQUIPMENT_SQL), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("Duplicate entry"));

        ApiResponse<Map<String, Object>> response = importController(jdbcTemplate).importEquipments(Map.of(
                "rows", List.of(Map.of(
                        "equipmentName", "异常设备",
                        "equipmentCode", "UNRESOLVED-DUP"
                ))
        ));

        assertEquals(200, response.code());
        assertEquals(1, response.data().get("totalCount"));
        assertEquals(0, response.data().get("skippedCount"));
        assertEquals(1, response.data().get("failureCount"));
        List<?> errors = (List<?>) response.data().get("errors");
        assertEquals(1, errors.size());
        assertTrue(String.valueOf(errors.getFirst()).contains("UNRESOLVED-DUP"));
    }

    private EquipmentController importController(JdbcTemplate jdbcTemplate) {
        return new EquipmentController(jdbcTemplate, mock(CustomFieldImportService.class));
    }
}
