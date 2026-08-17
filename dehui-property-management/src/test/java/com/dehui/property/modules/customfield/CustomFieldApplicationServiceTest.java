package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldApplicationService;
import com.dehui.property.modules.customfield.application.CustomFieldAuthorizationService;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.domain.ValidatedCustomFieldValue;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionRecord;
import com.dehui.property.security.AuthPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomFieldApplicationServiceTest {

    private final CustomFieldRepository repository = mock(CustomFieldRepository.class);
    private final CustomFieldApplicationService service = new CustomFieldApplicationService(
            new CustomFieldWorkspaceRegistry(),
            new CustomFieldAuthorizationService(),
            repository,
            new ObjectMapper()
    );

    @Test
    void createDefinitionEnforcesManageAndWorkspacePermissionBeforeWriting() {
        AuthPrincipal roomEditorWithoutGlobalPermission = principal("room:update");

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createDefinition(
                "rooms", Map.of("fieldName", "内部备注", "fieldType", "TEXT"),
                roomEditorWithoutGlobalPermission, "request-1"
        ));

        assertEquals(403, exception.getCode());
        verify(repository, never()).insertDefinition(any(), any());
    }

    @Test
    void createDefinitionPersistsDefinitionOptionsAndHistory() {
        when(repository.countActiveDefinitions("rooms")).thenReturn(0);
        when(repository.findDefinitions("rooms", false)).thenReturn(List.of());
        when(repository.insertDefinition(any(), eq(9L))).thenReturn(55L);
        when(repository.findDefinition("rooms", 55L)).thenReturn(Optional.of(definition(55L, "custom_priority")));

        Map<String, Object> result = service.createDefinition(
                "rooms",
                Map.of(
                        "fieldCode", "custom_priority",
                        "fieldName", "优先级",
                        "fieldType", "SINGLE_SELECT",
                        "options", List.of(
                                Map.of("optionCode", "high", "optionLabel", "高"),
                                Map.of("optionCode", "normal", "optionLabel", "普通")
                        )
                ),
                principal("custom-field:manage", "room:update"),
                "request-2"
        );

        assertEquals("custom_priority", result.get("fieldCode"));
        verify(repository).insertDefinition(any(), eq(9L));
        verify(repository).replaceOptions(eq(55L), any(), eq(9L));
        verify(repository).addHistory(eq(55L), eq("CREATE"), eq(null), any(), eq(9L), eq("request-2"));
    }

    @Test
    void createDefinitionRejectsRequiredFieldWhenExistingRowsWouldRemainEmpty() {
        DefinitionRecord required = new DefinitionRecord(
                56L, "CF-rooms-custom_required", "rooms", "building_room", "custom_required", "复核要求",
                CustomFieldType.TEXT, null, true, false, null, Map.of(), "[]",
                0, true, true, "ENABLED", 0L, List.of()
        );
        when(repository.countActiveDefinitions("rooms")).thenReturn(0);
        when(repository.findDefinitions("rooms", false)).thenReturn(List.of());
        when(repository.insertDefinition(any(), eq(9L))).thenReturn(56L);
        when(repository.findDefinition("rooms", 56L)).thenReturn(Optional.of(required));
        when(repository.hasMissingValues(any(), eq(56L))).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.createDefinition(
                "rooms",
                Map.of("fieldCode", "custom_required", "fieldName", "复核要求", "fieldType", "TEXT", "required", true),
                principal("custom-field:manage", "room:update"),
                "request-required"
        ));

        assertEquals(409, exception.getCode());
        verify(repository, never()).addHistory(eq(56L), any(), any(), any(), any(), any());
    }

    @Test
    void saveValuesValidatesEntityDefinitionAndTypedValueBeforeOneRepositoryWrite() {
        when(repository.entityExists(any(), eq(77L))).thenReturn(true);
        when(repository.findDefinitions("rooms", true)).thenReturn(List.of(definition(5L, "custom_note")));

        service.saveValues(
                "rooms",
                77L,
                Map.of("custom_note", "复核通过"),
                principal("room:update")
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<DefinitionRecord, ValidatedCustomFieldValue>> values = ArgumentCaptor.forClass(Map.class);
        verify(repository).replaceValues(any(), eq(77L), values.capture(), eq(9L));
        assertEquals(1, values.getValue().size());
    }

    @Test
    void saveValuesRejectsUnregisteredFieldCodes() {
        when(repository.entityExists(any(), eq(77L))).thenReturn(true);
        when(repository.findDefinitions("rooms", true)).thenReturn(List.of(definition(5L, "custom_note")));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.saveValues(
                "rooms", 77L, Map.of("custom_sql", "DROP TABLE"), principal("room:update")
        ));

        assertEquals(400, exception.getCode());
        verify(repository, never()).replaceValues(any(), any(), any(), any());
    }

    @Test
    void batchReadUsesOneRepositoryQueryForDistinctIds() {
        when(repository.findValues("rooms", List.of(1L, 2L))).thenReturn(Map.of());

        service.values("rooms", List.of(1L, 2L, 1L), principal("room:view"));

        verify(repository).findValues("rooms", List.of(1L, 2L));
    }

    @Test
    void requiredAndUniqueConstraintsAreEnforcedAgainstExistingData() {
        DefinitionRecord optionalUnique = new DefinitionRecord(
                6L, "CF-rooms-custom_serial", "rooms", "building_room", "custom_serial", "内部序号",
                CustomFieldType.TEXT, null, false, true, null, Map.of(), "[]",
                0, true, true, "ENABLED", 0L, List.of()
        );
        when(repository.entityExists(any(), eq(77L))).thenReturn(true);
        when(repository.findDefinitions("rooms", true)).thenReturn(List.of(optionalUnique));
        when(repository.uniqueValueExists(eq(optionalUnique), eq(77L), any())).thenReturn(true);

        BusinessException duplicate = assertThrows(BusinessException.class, () -> service.saveValues(
                "rooms", 77L, Map.of("custom_serial", "A-001"), principal("room:update")
        ));

        assertEquals(409, duplicate.getCode());
        verify(repository, never()).replaceValues(any(), any(), any(), any());
    }

    private static DefinitionRecord definition(Long id, String code) {
        return new DefinitionRecord(
                id, "CF-rooms-" + code, "rooms", "building_room", code, "测试字段",
                CustomFieldType.TEXT, null, false, false, null, Map.of(), "[]",
                0, true, true, "ENABLED", 0L, List.of()
        );
    }

    private static AuthPrincipal principal(String... permissions) {
        return new AuthPrincipal(9L, "tester", "SYSTEM", null, List.of(), List.of(permissions));
    }
}
