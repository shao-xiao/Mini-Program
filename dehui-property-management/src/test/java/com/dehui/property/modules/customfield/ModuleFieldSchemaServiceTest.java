package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.modules.customfield.application.ModuleFieldRegistry;
import com.dehui.property.modules.customfield.application.ModuleFieldSchemaService;
import com.dehui.property.security.AuthPrincipal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModuleFieldSchemaServiceTest {

    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            1L, "schema-test", "SYSTEM", null, List.of("ADMIN"), List.of("*")
    );

    @Test
    void acceptsEnumAndCamelCaseSurfaceNamesUsedByClients() {
        CustomFieldApplicationService customFields = mock(CustomFieldApplicationService.class);
        when(customFields.definitions(anyString(), org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(PRINCIPAL))).thenReturn(List.of());
        ModuleFieldSchemaService service = new ModuleFieldSchemaService(
                new ModuleFieldRegistry(), customFields
        );

        Map<String, Object> adminEnum = service.schema("workorders", "ADMIN_FORM", PRINCIPAL);
        Map<String, Object> adminCamel = service.schema("workorders", "adminForm", PRINCIPAL);
        Map<String, Object> mobileEnum = service.mobileSchema("workorders", "MINIPROGRAM_FORM", PRINCIPAL);
        Map<String, Object> mobileCamel = service.mobileSchema("workorders", "miniProgramForm", PRINCIPAL);

        assertEquals("adminForm", adminEnum.get("surface"));
        assertEquals(adminEnum.get("fields"), adminCamel.get("fields"));
        assertEquals("miniProgramForm", mobileEnum.get("surface"));
        assertEquals(mobileEnum.get("fields"), mobileCamel.get("fields"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void mobileWorkOrderFormContainsOnlyTheTenantRepairFields() {
        CustomFieldApplicationService customFields = mock(CustomFieldApplicationService.class);
        when(customFields.definitions(anyString(), org.mockito.ArgumentMatchers.eq(false),
                org.mockito.ArgumentMatchers.eq(PRINCIPAL))).thenReturn(List.of());
        ModuleFieldSchemaService service = new ModuleFieldSchemaService(
                new ModuleFieldRegistry(), customFields
        );

        List<Map<String, Object>> fields = (List<Map<String, Object>>) service
                .mobileSchema("workorders", "miniProgramForm", PRINCIPAL)
                .get("fields");
        Set<String> keys = fields.stream()
                .map(field -> String.valueOf(field.get("fieldKey")))
                .collect(java.util.stream.Collectors.toSet());

        assertTrue(keys.containsAll(Set.of(
                "contactName", "companyName", "reportedAt", "problemCategory",
                "description", "remark", "images"
        )));
        assertFalse(keys.stream().anyMatch(Set.of(
                "title", "orderType", "priority", "location", "attachments"
        )::contains));

        Map<String, Object> reporter = fields.stream()
                .filter(field -> "contactName".equals(field.get("fieldKey")))
                .findFirst()
                .orElseThrow();
        Map<String, Object> reportedAt = fields.stream()
                .filter(field -> "reportedAt".equals(field.get("fieldKey")))
                .findFirst()
                .orElseThrow();
        assertEquals("reporterName", reporter.get("apiField"));
        assertEquals(Boolean.TRUE, reportedAt.get("readonly"));
    }
}
