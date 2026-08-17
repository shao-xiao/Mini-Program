package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.modules.customfield.domain.CustomFieldType;
import org.junit.jupiter.api.Test;

class CustomFieldTypeTest {

    @Test
    void exposesOnlyImplementedTypesForFieldCreation() {
        assertTrue(CustomFieldType.TEXT.isImplemented());
        assertTrue(CustomFieldType.CURRENCY.isImplemented());
        assertTrue(CustomFieldType.MULTI_SELECT.isImplemented());
        assertTrue(CustomFieldType.IMAGE.isImplemented());
        assertTrue(CustomFieldType.ATTACHMENT.isImplemented());
        assertFalse(CustomFieldType.FORMULA.isImplemented());
        assertFalse(CustomFieldType.RELATION.isImplemented());
    }

    @Test
    void resolvesLegacyAndCanonicalWireNames() {
        assertEquals(CustomFieldType.TEXT, CustomFieldType.fromWireName("text"));
        assertEquals(CustomFieldType.CURRENCY, CustomFieldType.fromWireName("money"));
        assertEquals(CustomFieldType.SINGLE_SELECT, CustomFieldType.fromWireName("singleSelect"));
        assertEquals(CustomFieldType.MULTI_SELECT, CustomFieldType.fromWireName("multi-select"));
        assertEquals(CustomFieldType.BOOLEAN, CustomFieldType.fromWireName("checkbox"));
        assertEquals(CustomFieldType.DATETIME, CustomFieldType.fromWireName("date_time"));
        assertEquals(CustomFieldType.IMAGE, CustomFieldType.fromWireName("image"));
    }

    @Test
    void rejectsUnknownTypes() {
        assertThrows(IllegalArgumentException.class, () -> CustomFieldType.fromWireName("script"));
        assertThrows(IllegalArgumentException.class, () -> CustomFieldType.fromWireName(""));
    }
}
