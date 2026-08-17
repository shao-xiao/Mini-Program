package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dehui.property.modules.customfield.application.CustomFieldServerQueryAdapterRegistry;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CustomFieldServerQueryAdapterRegistryTest {

    private static final Pattern TRUSTED_EXPRESSION =
            Pattern.compile("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$");

    @Test
    void coversEveryServerWorkspaceExactlyOnceWithTrustedExpressions() {
        CustomFieldWorkspaceRegistry workspaceRegistry = new CustomFieldWorkspaceRegistry();
        CustomFieldServerQueryAdapterRegistry adapters =
                new CustomFieldServerQueryAdapterRegistry(workspaceRegistry);
        Set<String> expected = workspaceRegistry.registrations().stream()
                .filter(registration -> registration.queryMode() == CustomFieldWorkspaceRegistry.QueryMode.SERVER)
                .map(CustomFieldWorkspaceRegistry.Registration::workspaceKey)
                .collect(Collectors.toSet());

        assertEquals(expected, adapters.workspaceKeys());
        assertEquals(15, adapters.workspaceKeys().size());
        adapters.workspaceKeys().forEach(workspaceKey -> assertTrue(
                TRUSTED_EXPRESSION.matcher(adapters.require(workspaceKey).entityIdExpression()).matches(),
                workspaceKey
        ));
        assertThrows(IllegalArgumentException.class, () -> adapters.require("buildings"));
    }
}
