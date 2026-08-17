package com.dehui.property.modules.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.inspection.application.InspectionTypeValueResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InspectionTypeValueResolverTest {

    @Test
    void rejectsCustomTextWhenItIsNotInTheTypeDictionaryForNewRecords() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> InspectionTypeValueResolver.resolve(jdbcTemplate, "  夜间照明专项  ", null)
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void keepsAnUnchangedHistoricalCustomValueOnAdminEdit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        assertEquals(
                "夜间照明专项",
                InspectionTypeValueResolver.resolve(jdbcTemplate, "  夜间照明专项  ", "夜间照明专项")
        );
    }

    @Test
    void keepsHistoricalTextWhenAConfiguredTypeLaterUsesTheSameLabel() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "code", "NIGHT",
                "name", "夜间照明专项",
                "status", "ENABLED"
        )));

        assertEquals(
                "夜间照明专项",
                InspectionTypeValueResolver.resolve(jdbcTemplate, "夜间照明专项", "夜间照明专项")
        );
    }

    @Test
    void keepsDictionaryLabelsCompatibleBySavingTheirExistingCode() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "code", "FIRE",
                "name", "消防巡检",
                "status", "ENABLED"
        )));

        assertEquals(
                "FIRE",
                InspectionTypeValueResolver.resolve(jdbcTemplate, "消防巡检", null)
        );
    }

    @Test
    void rejectsValuesLongerThanTheExistingDatabaseColumn() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> InspectionTypeValueResolver.resolve(jdbcTemplate, "巡".repeat(33), null)
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void rejectsDictionaryCodesLongerThanTheInspectionRecordColumn() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "code", "X".repeat(33),
                "name", "Legacy short label",
                "status", "ENABLED"
        )));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> InspectionTypeValueResolver.resolve(jdbcTemplate, "Legacy short label", null)
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void rejectsBlankValues() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> InspectionTypeValueResolver.resolve(jdbcTemplate, "   ", null)
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void keepsAnUnchangedDisabledLegacyCodeOnAdminEdit() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "code", "LEGACY",
                "name", "Legacy type",
                "status", "DISABLED"
        )));

        assertEquals(
                "LEGACY",
                InspectionTypeValueResolver.resolve(jdbcTemplate, "LEGACY", "LEGACY")
        );
    }

    @Test
    void rejectsDisabledDictionaryTypesForNewRecords() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "code", "LEGACY",
                "name", "Legacy type",
                "status", "DISABLED"
        )));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> InspectionTypeValueResolver.resolve(jdbcTemplate, "LEGACY", null)
        );

        assertEquals(400, error.getCode());
    }
}
