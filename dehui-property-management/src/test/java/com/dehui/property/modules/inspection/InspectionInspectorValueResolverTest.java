package com.dehui.property.modules.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.inspection.application.InspectionInspectorValueResolver;
import org.junit.jupiter.api.Test;

class InspectionInspectorValueResolverTest {

    @Test
    void trimsAndPreservesCustomInspectorText() {
        assertEquals(
                "夜班联合巡检组",
                InspectionInspectorValueResolver.resolve("  夜班联合巡检组  ")
        );
    }

    @Test
    void acceptsTheExistingDatabaseColumnLimit() {
        String value = "巡".repeat(64);

        assertEquals(value, InspectionInspectorValueResolver.resolve(value));
    }

    @Test
    void rejectsBlankInspectorText() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> InspectionInspectorValueResolver.resolve("   ")
        );

        assertEquals(400, error.getCode());
    }

    @Test
    void rejectsInspectorTextLongerThanTheDatabaseColumn() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> InspectionInspectorValueResolver.resolve("巡".repeat(65))
        );

        assertEquals(400, error.getCode());
    }
}
