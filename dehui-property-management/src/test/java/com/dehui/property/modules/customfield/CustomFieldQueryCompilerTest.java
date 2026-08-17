package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionRecord;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.OptionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CustomFieldQueryCompilerTest {

    private final CustomFieldQueryCompiler compiler = new CustomFieldQueryCompiler(
            new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-07-21T00:00:00Z"), ZoneOffset.UTC)
    );

    @Test
    void objectMapperConstructorIsSelectedForRuntimeInjection() throws Exception {
        var constructor = CustomFieldQueryCompiler.class.getDeclaredConstructor(ObjectMapper.class);

        assertTrue(constructor.isAnnotationPresent(Autowired.class));
    }

    @Test
    void compilesTextAndNumericFiltersWithoutPuttingFieldCodesOrValuesIntoSql() {
        String filters = """
                [
                  {"field":"custom.custom_note","operator":"contains","value":"A%_"},
                  {"field":"custom_score","operator":"between","value":[10,20]}
                ]
                """;

        CompiledQuery query = compiler.compile(
                "record.id", "room", definitions(), filters, null, null
        );

        assertTrue(query.predicateSql().contains("LOCATE(LOWER(?), LOWER(cfv_filter_0.value_long_text)) > 0"));
        assertTrue(query.predicateSql().contains("cfv_filter_1.value_number BETWEEN ? AND ?"));
        assertFalse(query.predicateSql().contains("custom_note"));
        assertFalse(query.predicateSql().contains("A%_"));
        assertEquals(List.of(6L, "room", "A%_", 2L, "room", 10L, 20L),
                normalizeNumbers(query.predicateArguments()));
        assertEquals(2, query.filterCount());
    }

    @Test
    void supportsSelectMultiSelectDateBooleanAndEmptySemantics() {
        String filters = """
                [
                  {"field":"custom.customer_level","operator":"containsAny","value":["A","B"]},
                  {"field":"custom_tags","operator":"containsAll","value":["urgent","vip"]},
                  {"field":"custom_review_date","operator":"thisMonth"},
                  {"field":"custom_enabled","operator":"equals","value":true},
                  {"field":"custom_note","operator":"empty"}
                ]
                """;

        CompiledQuery query = compiler.compile("record.id", "room", definitions(), filters, null, null);

        assertTrue(query.predicateSql().contains("value_text IN (?,?)"));
        assertTrue(query.predicateSql().contains("JSON_CONTAINS(cfv_filter_1.value_json, JSON_QUOTE(?), '$')"));
        assertTrue(query.predicateSql().contains("cfv_filter_2.value_date BETWEEN ? AND ?"));
        assertTrue(query.predicateSql().contains("cfv_filter_3.value_boolean = ?"));
        assertTrue(query.predicateSql().contains("NOT EXISTS"));
        assertTrue(query.predicateArguments().contains(java.time.LocalDate.of(2026, 7, 1)));
        assertTrue(query.predicateArguments().contains(java.time.LocalDate.of(2026, 7, 31)));
    }

    @Test
    void groupOrderingPrecedesSortAndDuplicateCustomFieldsAreIgnored() {
        CompiledQuery query = compiler.compile(
                "record.id",
                "room",
                definitions(),
                null,
                "nativeStatus:asc,custom.customer_level:asc",
                "custom.customer_level:desc,custom_score:desc,nativeName:asc"
        );

        assertEquals(2, query.orderExpressions().size());
        assertTrue(query.orderExpressions().get(0).contains("value_text"));
        assertTrue(query.orderExpressions().get(0).endsWith("ASC"));
        assertTrue(query.orderExpressions().get(1).contains("value_number"));
        assertTrue(query.orderExpressions().get(1).endsWith("DESC"));
        assertEquals(List.of(1L, "room", 2L, "room"), query.orderArguments());
    }

    @Test
    void mergesNativeAndCustomOrderingInTheExactRequestedSequence() {
        NativeOrderPolicy policy = NativeOrderPolicy.of(
                Map.of("status", "record.status", "name", "record.name"),
                null,
                null,
                "record.updated_at",
                "record.id"
        );

        CompiledQuery query = compiler.compile(
                "record.id",
                "room",
                definitions(),
                null,
                "status:asc,custom.customer_level:desc",
                "custom_score:desc,name:asc,status:desc",
                policy
        );

        assertEquals(5, query.orderExpressions().size());
        assertEquals("record.status ASC", query.orderExpressions().get(0));
        assertTrue(query.orderExpressions().get(1).contains("value_text"));
        assertTrue(query.orderExpressions().get(2).contains("value_number"));
        assertEquals("record.name ASC", query.orderExpressions().get(3));
        assertEquals("record.id DESC", query.orderExpressions().get(4));
        assertEquals(List.of(1L, "room", 2L, "room"), query.orderArguments());
    }

    @Test
    void rejectsUnknownFieldsOperatorsUnsafeIdentifiersAndInjectionLikeInput() {
        BusinessException unknown = assertThrows(BusinessException.class, () -> compiler.compile(
                "record.id", "room", definitions(),
                "[{\"field\":\"custom.missing\",\"operator\":\"equals\",\"value\":\"x\"}]",
                null,
                null
        ));
        assertEquals(400, unknown.getCode());

        assertThrows(BusinessException.class, () -> compiler.compile(
                "record.id", "room", definitions(),
                "[{\"field\":\"custom.customer_level OR 1=1\",\"operator\":\"equals\",\"value\":\"A\"}]",
                null,
                null
        ));
        assertThrows(BusinessException.class, () -> compiler.compile(
                "record.id", "room", definitions(),
                "[{\"field\":\"custom_score\",\"operator\":\"contains\",\"value\":\"1\"}]",
                null,
                null
        ));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(
                "record.id OR 1=1", "room", definitions(), null, null, null
        ));
    }

    @Test
    void rejectsGroupingAndSortingWhenDefinitionCapabilityIsDisabled() {
        assertThrows(BusinessException.class, () -> compiler.compile(
                "record.id", "room", definitions(), null, "custom_tags:asc", null
        ));
        assertThrows(BusinessException.class, () -> compiler.compile(
                "record.id", "room", definitions(), null, null, "custom_note:asc"
        ));
    }

    @Test
    void rejectsUnknownAndDisabledOptionCodes() {
        assertThrows(BusinessException.class, () -> compiler.compile(
                "record.id", "room", definitions(),
                "[{\"field\":\"custom.customer_level\",\"operator\":\"equals\",\"value\":\"missing\"}]",
                null,
                null
        ));
        assertThrows(BusinessException.class, () -> compiler.compile(
                "record.id", "room", definitions(),
                "[{\"field\":\"custom.customer_level\",\"operator\":\"equals\",\"value\":\"disabled\"}]",
                null,
                null
        ));
    }

    private List<DefinitionRecord> definitions() {
        return List.of(
                definition(1L, "customer_level", CustomFieldType.SINGLE_SELECT, true, true),
                definition(2L, "custom_score", CustomFieldType.NUMBER, true, true),
                definition(3L, "custom_tags", CustomFieldType.MULTI_SELECT, true, false),
                definition(4L, "custom_review_date", CustomFieldType.DATE, true, true),
                definition(5L, "custom_enabled", CustomFieldType.BOOLEAN, true, true),
                definition(6L, "custom_note", CustomFieldType.LONG_TEXT, false, false)
        );
    }

    private DefinitionRecord definition(
            Long id,
            String code,
            CustomFieldType type,
            boolean sortable,
            boolean groupable
    ) {
        List<OptionRecord> options = switch (type) {
            case SINGLE_SELECT -> List.of(
                    new OptionRecord(1L, "A", "A", null, 1, "ENABLED"),
                    new OptionRecord(2L, "B", "B", null, 2, "ENABLED"),
                    new OptionRecord(3L, "disabled", "Disabled", null, 3, "DISABLED")
            );
            case MULTI_SELECT -> List.of(
                    new OptionRecord(4L, "urgent", "Urgent", null, 1, "ENABLED"),
                    new OptionRecord(5L, "vip", "VIP", null, 2, "ENABLED")
            );
            default -> List.of();
        };
        return new DefinitionRecord(
                id,
                "CF-" + code,
                "rooms",
                "room",
                code,
                code,
                type,
                null,
                false,
                false,
                null,
                Map.of(),
                null,
                id.intValue(),
                sortable,
                groupable,
                "ENABLED",
                0,
                options
        );
    }

    private List<Object> normalizeNumbers(List<Object> arguments) {
        return arguments.stream().map(value -> {
            if (value instanceof java.math.BigDecimal decimal) return decimal.longValue();
            return value;
        }).toList();
    }
}
