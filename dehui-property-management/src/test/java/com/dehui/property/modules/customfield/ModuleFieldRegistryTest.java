package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.modules.customfield.application.ModuleFieldRegistry;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleFieldRegistryTest {

    @Test
    void pilotModulesExposeUniqueStableFieldKeysAndApiMappings() {
        ModuleFieldRegistry registry = new ModuleFieldRegistry();

        for (String moduleKey : registry.registeredModules()) {
            var fields = registry.systemFields(moduleKey);
            assertFalse(fields.isEmpty());
            var keys = new HashSet<String>();
            fields.forEach(field -> {
                String fieldKey = String.valueOf(field.get("fieldKey"));
                assertFalse(fieldKey.isBlank());
                assertFalse(String.valueOf(field.get("apiField")).isBlank());
                assertNotNull(field.get("surfaces"));
                assertFalse(!keys.add(fieldKey), moduleKey + " contains duplicate " + fieldKey);
            });
        }
        assertEquals("workorders", registry.canonicalModuleKey("workorder"));
        assertEquals("inspections", registry.canonicalModuleKey("inspection"));
        assertEquals("visitors", registry.canonicalModuleKey("visitor"));
    }

    @Test
    void phaseFourPilotModulesExposeCanonicalSystemKeys() {
        ModuleFieldRegistry registry = new ModuleFieldRegistry();
        assertEquals(
                Set.of("code", "title", "orderType", "problemCategory", "status", "priority",
                        "contactName", "contactPhone", "companyName", "reportedAt", "location",
                        "description", "images", "attachments", "remark", "createdTime"),
                keys(registry, "workorders")
        );
        assertTrue(keys(registry, "inspections").containsAll(Set.of(
                "inspectionDate", "inspector", "inspectionType", "area", "target", "result",
                "problemDescription", "actionTaken", "status", "images", "remark"
        )));
        assertTrue(keys(registry, "visitors").containsAll(Set.of(
                "visitorName", "visitorPhone", "companyName", "plateNumber", "visitDate",
                "visitTime", "visitReason", "remark", "status"
        )));
        assertTrue(keys(registry, "meeting-bookings").containsAll(Set.of(
                "meetingRoomId", "startTime", "endTime", "purpose", "participantCount",
                "departmentName", "contactName", "contactPhone", "remark", "status"
        )));
        assertTrue(readonly(registry, "meeting-bookings", "contactName"));
        assertTrue(readonly(registry, "meeting-bookings", "contactPhone"));
    }

    @Test
    void mobilePilotSchemasDoNotRegisterRelationshipIdentifiers() {
        ModuleFieldRegistry registry = new ModuleFieldRegistry();
        for (String module : Set.of("workorders", "inspections", "visitors")) {
            assertFalse(keys(registry, module).stream().anyMatch(Set.of(
                    "tenantId", "tenantContactId", "mobileUserId", "sysUserId",
                    "assigneeId", "inspectorId", "operatorId"
            )::contains));
        }
    }

    @Test
    void parkingAssignmentPlateNumberIsOptionalForActivitySpaces() {
        ModuleFieldRegistry registry = new ModuleFieldRegistry();

        boolean required = registry.systemFields("parking-assignments").stream()
                .filter(field -> "plateNo".equals(String.valueOf(field.get("fieldKey"))))
                .map(field -> Boolean.TRUE.equals(field.get("required")))
                .findFirst()
                .orElseThrow();

        assertFalse(required);
    }

    @Test
    void inspectionTypeUsesDictionarySelectionForAdminAndMiniProgramForms() {
        ModuleFieldRegistry registry = new ModuleFieldRegistry();

        Map<String, Object> inspectionType = registry.systemFields("inspections").stream()
                .filter(field -> "inspectionType".equals(String.valueOf(field.get("fieldKey"))))
                .findFirst()
                .orElseThrow();

        assertEquals("SELECT", inspectionType.get("dataType"));
        assertEquals(true, inspectionType.get("required"));
        assertEquals(false, inspectionType.get("readonly"));
        assertEquals("/mobile/staff/inspection-types", inspectionType.get("optionsSource"));
        assertFalse(((Map<?, ?>) inspectionType.get("validation")).containsKey("maxLength"));
    }

    @Test
    void inspectionPersonIsRequiredFreeTextForAdminAndMiniProgramForms() {
        ModuleFieldRegistry registry = new ModuleFieldRegistry();

        Map<String, Object> inspector = registry.systemFields("inspections").stream()
                .filter(field -> "inspector".equals(String.valueOf(field.get("fieldKey"))))
                .findFirst()
                .orElseThrow();

        assertEquals("TEXT", inspector.get("dataType"));
        assertEquals(true, inspector.get("required"));
        assertEquals(false, inspector.get("readonly"));
        assertFalse(inspector.containsKey("optionsSource"));
        assertEquals(64, ((Map<?, ?>) inspector.get("validation")).get("maxLength"));
    }

    private Set<String> keys(ModuleFieldRegistry registry, String module) {
        return registry.systemFields(module).stream()
                .map(field -> String.valueOf(field.get("fieldKey")))
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean readonly(ModuleFieldRegistry registry, String module, String fieldKey) {
        return registry.systemFields(module).stream()
                .filter(field -> fieldKey.equals(String.valueOf(field.get("fieldKey"))))
                .map(field -> Boolean.TRUE.equals(field.get("readonly")))
                .findFirst()
                .orElse(false);
    }
}
