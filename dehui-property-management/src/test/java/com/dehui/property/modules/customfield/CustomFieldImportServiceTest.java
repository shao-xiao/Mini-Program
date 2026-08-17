package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldImportService;
import com.dehui.property.modules.customfield.application.CustomFieldImportService.ImportFieldDefinition;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionRecord;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomFieldImportServiceTest {

    private static final ImportFieldDefinition QUANTITY = new ImportFieldDefinition(
            "custom_quantity", "数量", CustomFieldType.NUMBER, "设备导入数量", 10, true, false
    );

    @Test
    void createsMissingDefinitionThroughPlatformRepository() {
        CustomFieldRepository repository = mock(CustomFieldRepository.class);
        CustomFieldImportService service = service(repository);
        DefinitionRecord created = definition(11L, CustomFieldType.NUMBER);
        when(repository.findDefinition("equipment", "custom_quantity")).thenReturn(Optional.empty());
        when(repository.countActiveDefinitions("equipment")).thenReturn(0);
        when(repository.insertDefinition(any(), eq(0L))).thenReturn(11L);
        when(repository.findDefinition("equipment", 11L)).thenReturn(Optional.of(created));

        service.ensureDefinitions("equipment", List.of(QUANTITY));

        verify(repository).insertDefinition(any(), eq(0L));
        verify(repository).addHistory(eq(11L), eq("IMPORT_CREATE"), eq(null), any(), eq(0L), any());
    }

    @Test
    void rejectsExistingDefinitionWithIncompatibleType() {
        CustomFieldRepository repository = mock(CustomFieldRepository.class);
        CustomFieldImportService service = service(repository);
        when(repository.findDefinition("equipment", "custom_quantity"))
                .thenReturn(Optional.of(definition(11L, CustomFieldType.TEXT)));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.ensureDefinitions("equipment", List.of(QUANTITY))
        );

        assertEquals(409, exception.getCode());
    }

    @Test
    void validatesAndUpsertsOnlySubmittedNonBlankValues() {
        CustomFieldRepository repository = mock(CustomFieldRepository.class);
        CustomFieldImportService service = service(repository);
        CustomFieldWorkspaceRegistry registry = new CustomFieldWorkspaceRegistry();
        DefinitionRecord quantity = definition(11L, CustomFieldType.NUMBER);
        when(repository.entityExists(registry.require("equipment"), 88L)).thenReturn(true);
        when(repository.findDefinition("equipment", "custom_quantity")).thenReturn(Optional.of(quantity));

        service.saveValues("equipment", 88L, Map.of(
                "custom_quantity", "10",
                "custom_manufacturer_contact", " "
        ));

        verify(repository).upsertValues(
                eq(registry.require("equipment")),
                eq(88L),
                org.mockito.ArgumentMatchers.argThat(values -> values.size() == 1
                        && new BigDecimal("10.000000").compareTo(values.get(quantity).numberValue()) == 0),
                eq(0L)
        );
    }

    @Test
    void mapsImportedColumnsByStableCodeOrVisibleFieldName() {
        CustomFieldRepository repository = mock(CustomFieldRepository.class);
        CustomFieldImportService service = service(repository);
        CustomFieldWorkspaceRegistry registry = new CustomFieldWorkspaceRegistry();
        DefinitionRecord quantity = definition(11L, CustomFieldType.NUMBER);
        when(repository.findDefinitions("equipment", true)).thenReturn(List.of(quantity));
        when(repository.entityExists(registry.require("equipment"), 88L)).thenReturn(true);
        when(repository.findDefinition("equipment", "custom_quantity")).thenReturn(Optional.of(quantity));

        service.saveImportedRow("equipment", 88L, Map.of("数量", "12"), Map.of());

        verify(repository).upsertValues(
                eq(registry.require("equipment")),
                eq(88L),
                org.mockito.ArgumentMatchers.argThat(values -> values.size() == 1
                        && new BigDecimal("12").compareTo(values.get(quantity).numberValue()) == 0),
                eq(0L)
        );
    }

    private CustomFieldImportService service(CustomFieldRepository repository) {
        return new CustomFieldImportService(new CustomFieldWorkspaceRegistry(), repository);
    }

    private DefinitionRecord definition(Long id, CustomFieldType type) {
        return new DefinitionRecord(
                id,
                "CF-equipment-custom_quantity",
                "equipment",
                "equipment",
                "custom_quantity",
                "数量",
                type,
                "设备导入数量",
                false,
                false,
                null,
                Map.of(),
                "[]",
                10,
                true,
                false,
                "ENABLED",
                0L,
                List.of()
        );
    }
}
