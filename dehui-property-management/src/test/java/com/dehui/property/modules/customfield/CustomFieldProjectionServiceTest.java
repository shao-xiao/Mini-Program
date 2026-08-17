package com.dehui.property.modules.customfield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.customfield.application.CustomFieldProjectionService;
import com.dehui.property.modules.customfield.application.CustomFieldWorkspaceRegistry;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import java.util.LinkedHashMap;
import java.util.stream.LongStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CustomFieldProjectionServiceTest {

    @Test
    void attachesOneBatchOfValuesWithoutMutatingBusinessRowsOrPagination() {
        CustomFieldRepository repository = Mockito.mock(CustomFieldRepository.class);
        when(repository.findValues("rooms", List.of(10L, 11L))).thenReturn(Map.of(
                10L, Map.of("custom_level", "A")
        ));
        CustomFieldProjectionService service = new CustomFieldProjectionService(
                new CustomFieldWorkspaceRegistry(),
                repository
        );
        Map<String, Object> first = new LinkedHashMap<>(Map.of("id", 10L, "name", "A101"));
        Map<String, Object> second = new LinkedHashMap<>(Map.of("id", 11L, "name", "A102"));

        PageResponse<Map<String, Object>> result = service.attach(
                "rooms",
                new PageResponse<>(List.of(first, second), 28, 2, 10)
        );

        assertEquals(Map.of("custom_level", "A"), result.records().get(0).get("customFields"));
        assertEquals(Map.of(), result.records().get(1).get("customFields"));
        assertEquals(28, result.total());
        assertEquals(2, result.page());
        assertEquals(10, result.pageSize());
        assertEquals(false, first.containsKey("customFields"));
        verify(repository).findValues("rooms", List.of(10L, 11L));
    }

    @Test
    void attachesTypedDtoPagesWithTheSameSingleBatchContract() {
        CustomFieldRepository repository = Mockito.mock(CustomFieldRepository.class);
        when(repository.findValues("fee-rules", List.of(5L))).thenReturn(Map.of(
                5L, Map.of("approval_level", "strict")
        ));
        CustomFieldProjectionService service = new CustomFieldProjectionService(
                new CustomFieldWorkspaceRegistry(),
                repository
        );

        PageResponse<TestRow> result = service.attach(
                "fee-rules",
                new PageResponse<>(List.of(new TestRow(5L, Map.of())), 1, 1, 20),
                TestRow::id,
                TestRow::withCustomFields
        );

        assertEquals(Map.of("approval_level", "strict"), result.records().getFirst().customFields());
        verify(repository).findValues("fee-rules", List.of(5L));
    }

    @Test
    void chunksLargeInternalExportsWithoutDroppingEntityIds() {
        CustomFieldRepository repository = Mockito.mock(CustomFieldRepository.class);
        CustomFieldProjectionService service = new CustomFieldProjectionService(
                new CustomFieldWorkspaceRegistry(),
                repository
        );
        List<Long> ids = LongStream.rangeClosed(1, 201).boxed().toList();

        service.values("energy-records", ids);

        verify(repository).findValues("energy-records", ids.subList(0, 200));
        verify(repository).findValues("energy-records", ids.subList(200, 201));
    }

    private record TestRow(Long id, Map<String, Object> customFields) {
        private TestRow withCustomFields(Map<String, Object> values) {
            return new TestRow(id, values);
        }
    }
}
