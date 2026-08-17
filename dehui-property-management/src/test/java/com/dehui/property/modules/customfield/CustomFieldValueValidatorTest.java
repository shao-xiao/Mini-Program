package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.domain.CustomFieldDefinitionSnapshot;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.domain.CustomFieldValueValidator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CustomFieldValueValidatorTest {

    private final CustomFieldValueValidator validator = new CustomFieldValueValidator();

    @Test
    void validatesTypedNumberCurrencyDateAndBooleanValues() {
        assertEquals(new BigDecimal("12.500000"), validator.validate(definition(CustomFieldType.NUMBER), "12.5").numberValue());
        assertEquals(new BigDecimal("88.20"), validator.validate(definition(CustomFieldType.CURRENCY), "88.2").decimalValue());
        assertEquals(LocalDate.of(2026, 7, 21), validator.validate(definition(CustomFieldType.DATE), "2026-07-21").dateValue());
        assertEquals(true, validator.validate(definition(CustomFieldType.BOOLEAN), "true").booleanValue());
    }

    @Test
    void validatesStableOptionCodesInsteadOfLabels() {
        CustomFieldDefinitionSnapshot single = definition(CustomFieldType.SINGLE_SELECT, Set.of("priority_a", "priority_b"));
        assertEquals("priority_a", validator.validate(single, "priority_a").textValue());
        assertThrows(BusinessException.class, () -> validator.validate(single, "A级"));

        CustomFieldDefinitionSnapshot multiple = definition(CustomFieldType.MULTI_SELECT, Set.of("a", "b", "c"));
        assertEquals(List.of("a", "c"), validator.validate(multiple, List.of("a", "c")).jsonValue());
        assertThrows(BusinessException.class, () -> validator.validate(multiple, List.of("a", "missing")));
    }

    @Test
    void enforcesRequiredAndConfiguredTextLength() {
        CustomFieldDefinitionSnapshot required = new CustomFieldDefinitionSnapshot(
                1L, "rooms", "building_room", "custom_note", "内部备注", CustomFieldType.TEXT,
                true, false, Map.of("maxLength", 4), Set.of(), 0L
        );
        assertThrows(BusinessException.class, () -> validator.validate(required, ""));
        assertThrows(BusinessException.class, () -> validator.validate(required, "12345"));
        assertEquals("1234", validator.validate(required, "1234").textValue());
    }

    @Test
    void acceptsEmptyOptionalValuesWithoutWritingTypedColumns() {
        var value = validator.validate(definition(CustomFieldType.TEXT), null);
        assertNull(value.textValue());
        assertNull(value.jsonValue());
    }

    @Test
    void validatesEmailAndHttpUrlOnTheServer() {
        assertEquals("ops@example.com", validator.validate(
                definition(CustomFieldType.EMAIL), "ops@example.com"
        ).textValue());
        assertEquals("https://example.com/check", validator.validate(
                definition(CustomFieldType.URL), "https://example.com/check"
        ).textValue());
        assertThrows(BusinessException.class, () -> validator.validate(
                definition(CustomFieldType.EMAIL), "not-an-email"
        ));
        assertThrows(BusinessException.class, () -> validator.validate(
                definition(CustomFieldType.URL), "javascript:alert(1)"
        ));
    }

    private static CustomFieldDefinitionSnapshot definition(CustomFieldType type) {
        return definition(type, Set.of());
    }

    private static CustomFieldDefinitionSnapshot definition(CustomFieldType type, Set<String> options) {
        return new CustomFieldDefinitionSnapshot(
                1L, "rooms", "building_room", "custom_test", "测试字段", type,
                false, false, Map.of(), options, 0L
        );
    }
}
