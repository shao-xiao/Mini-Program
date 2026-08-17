package com.dehui.property.modules.customfield;

import com.dehui.property.modules.customfield.domain.CustomFieldType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomFieldFileTypeContractTest {

    @Test
    void imageAndAttachmentAreDistinctImplementedTypes() {
        assertEquals(CustomFieldType.IMAGE, CustomFieldType.fromWireName("IMAGE"));
        assertEquals(CustomFieldType.ATTACHMENT, CustomFieldType.fromWireName("ATTACHMENT"));
        assertNotEquals(CustomFieldType.IMAGE, CustomFieldType.ATTACHMENT);
        assertTrue(CustomFieldType.IMAGE.isImplemented());
        assertTrue(CustomFieldType.ATTACHMENT.isImplemented());
        assertEquals("value_json", CustomFieldType.IMAGE.storageColumn());
        assertEquals("value_json", CustomFieldType.ATTACHMENT.storageColumn());
    }
}
