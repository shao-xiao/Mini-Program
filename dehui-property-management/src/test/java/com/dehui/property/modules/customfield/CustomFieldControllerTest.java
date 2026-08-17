package com.dehui.property.modules.customfield;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.modules.customfield.controller.CustomFieldController;
import com.dehui.property.security.AuthPrincipal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomFieldControllerTest {

    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            7L,
            "field-manager",
            "SYSTEM",
            null,
            List.of("PROPERTY_MANAGER"),
            List.of("custom-field:manage", "parking:update")
    );

    @Test
    void createDefinitionDelegatesToApplicationServiceWithoutSql() {
        CustomFieldApplicationService service = mock(CustomFieldApplicationService.class);
        Map<String, Object> body = Map.of(
                "key", "custom_owner",
                "label", "负责人",
                "type", "text"
        );
        when(service.createDefinition("parking-spaces", body, PRINCIPAL, "request-1"))
                .thenReturn(Map.of("fieldCode", "custom_owner"));
        CustomFieldController controller = new CustomFieldController(service);

        controller.createDefinition("parking-spaces", body, "request-1", PRINCIPAL);

        verify(service).createDefinition("parking-spaces", body, PRINCIPAL, "request-1");
    }

    @Test
    void saveValuesDelegatesTypedValidationAndPersistenceToApplicationService() {
        CustomFieldApplicationService service = mock(CustomFieldApplicationService.class);
        CustomFieldController controller = new CustomFieldController(service);
        Map<String, Object> values = Map.of("custom_priority", "紧急");

        controller.saveRecordValues("parking-spaces", 9L, values, PRINCIPAL);

        verify(service).saveValues(eq("parking-spaces"), eq(9L), eq(values), eq(PRINCIPAL));
    }
}
