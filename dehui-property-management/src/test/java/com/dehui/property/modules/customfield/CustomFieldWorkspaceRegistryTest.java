package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class CustomFieldWorkspaceRegistryTest {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]*$");
    private final CustomFieldWorkspaceRegistry registry = new CustomFieldWorkspaceRegistry();

    @Test
    void registersEveryFormalDataWorkspaceExactlyOnce() {
        assertEquals(27, registry.registrations().size());

        Set<String> workspaceKeys = new HashSet<>();
        Set<String> entityTypes = new HashSet<>();
        registry.registrations().forEach(registration -> {
            assertTrue(workspaceKeys.add(registration.workspaceKey()), registration.workspaceKey());
            assertTrue(entityTypes.add(registration.entityType()), registration.entityType());
        });
    }

    @Test
    void registrationsUseSafeIdentifiersAndRealPermissionCodes() {
        registry.registrations().forEach(registration -> {
            assertTrue(SQL_IDENTIFIER.matcher(registration.tableName()).matches(), registration.tableName());
            assertTrue(SQL_IDENTIFIER.matcher(registration.idColumn()).matches(), registration.idColumn());
            assertTrue(registration.viewPermission().contains(":"), registration.workspaceKey());
            assertFalse(registration.valueUpdatePermissions().isEmpty(), registration.workspaceKey());
            registration.valueUpdatePermissions().forEach(permission ->
                    assertTrue(permission.contains(":"), registration.workspaceKey()));
            assertTrue(registration.maximumCustomFields() > 0);
        });
    }

    @Test
    void exposesClientAndServerQueryModesWithoutGuessingUnknownWorkspaces() {
        assertEquals(CustomFieldWorkspaceRegistry.QueryMode.CLIENT,
                registry.require("buildings").queryMode());
        assertEquals(CustomFieldWorkspaceRegistry.QueryMode.SERVER,
                registry.require("rooms").queryMode());
        assertEquals("notice", registry.require("announcements").tableName());
        assertEquals("billing_plan", registry.require("billing-plans").tableName());
        assertThrows(IllegalArgumentException.class, () -> registry.require("arbitrary-table"));
    }
}
