package com.dehui.property.modules.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.contract.controller.ContractController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ContractControllerTest {

    @Test
    void createContractPersistsMultipleRooms() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(
                "SELECT id FROM contract WHERE code = ? AND deleted = 0 ORDER BY id DESC LIMIT 1",
                "CON-MULTI"
        )).thenReturn(List.of(Map.of("id", 88L)));
        when(jdbcTemplate.queryForList(
                "SELECT room_id FROM contract_room WHERE contract_id = ? AND deleted = 0 ORDER BY id",
                88L
        )).thenReturn(List.of());

        ApiResponse<Void> response = new ContractController(jdbcTemplate).createContract(Map.of(
                "contractNumber", "CON-MULTI",
                "tenantId", 1L,
                "roomIds", List.of(801L, 802L, 803L),
                "contactPhone", "13800000000",
                "startDate", "2026-07-01",
                "endDate", "2027-06-30"
        ));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                eq("INSERT INTO contract_room (contract_id, room_id, created_by, updated_by) VALUES (?, ?, ?, ?)"),
                eq(88L),
                eq(801L),
                eq(0L),
                eq(0L)
        );
        verify(jdbcTemplate).update(
                eq("INSERT INTO contract_room (contract_id, room_id, created_by, updated_by) VALUES (?, ?, ?, ?)"),
                eq(88L),
                eq(802L),
                eq(0L),
                eq(0L)
        );
        verify(jdbcTemplate).update(
                eq("INSERT INTO contract_room (contract_id, room_id, created_by, updated_by) VALUES (?, ?, ?, ?)"),
                eq(88L),
                eq(803L),
                eq(0L),
                eq(0L)
        );
    }

    @Test
    void createContractRejectsInvalidContactPhone() {
        ContractController controller = new ContractController(mock(JdbcTemplate.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.createContract(Map.of(
                "tenantId", 1L,
                "roomId", 2L,
                "contactPhone", "1384893人9"
        )));

        assertEquals(400, exception.getCode());
        assertEquals("联系电话格式不正确", exception.getMessage());
    }

    @Test
    void createContractRejectsInvalidContactEmail() {
        ContractController controller = new ContractController(mock(JdbcTemplate.class));

        BusinessException exception = assertThrows(BusinessException.class, () -> controller.createContract(Map.of(
                "tenantId", 1L,
                "roomId", 2L,
                "contactPhone", "13800000000",
                "contactEmail", "12312nkls"
        )));

        assertEquals(400, exception.getCode());
        assertEquals("邮箱格式不正确", exception.getMessage());
    }

    @Test
    void terminateReleasesRoomAndTerminatesContract() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT room_id FROM contract_room WHERE contract_id = ? AND deleted = 0 ORDER BY id", 9L))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList("SELECT room_id FROM contract WHERE id = ? AND deleted = 0", 9L))
                .thenReturn(List.of(Map.of("room_id", 12L)));
        when(jdbcTemplate.update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "TERMINATED", 0L, 9L))
                .thenReturn(1);

        ApiResponse<Void> response = new ContractController(jdbcTemplate).terminate(9L, Map.of("reason", "test"));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("UPDATE building_room SET rent_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "VACANT", 0L, 12L);
        verify(jdbcTemplate).update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "TERMINATED", 0L, 9L);
    }

    @Test
    void cancelReleasesRoomAndCancelsContract() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT room_id FROM contract_room WHERE contract_id = ? AND deleted = 0 ORDER BY id", 9L))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList("SELECT room_id FROM contract WHERE id = ? AND deleted = 0", 9L))
                .thenReturn(List.of(Map.of("room_id", 12L)));
        when(jdbcTemplate.update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "CANCELLED", 0L, 9L))
                .thenReturn(1);

        ApiResponse<Void> response = new ContractController(jdbcTemplate).cancel(9L, Map.of("reason", "test"));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("UPDATE building_room SET rent_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "VACANT", 0L, 12L);
        verify(jdbcTemplate).update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "CANCELLED", 0L, 9L);
    }

    @Test
    void reactivateRentsRoomAndRestoresActiveContract() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT room_id FROM contract_room WHERE contract_id = ? AND deleted = 0 ORDER BY id", 9L))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForList("SELECT room_id FROM contract WHERE id = ? AND deleted = 0", 9L))
                .thenReturn(List.of(Map.of("room_id", 12L)));
        when(jdbcTemplate.update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "ACTIVE", 0L, 9L))
                .thenReturn(1);

        ApiResponse<Void> response = new ContractController(jdbcTemplate).reactivate(9L, Map.of("reason", "test"));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("UPDATE building_room SET rent_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "RENTED", 0L, 12L);
        verify(jdbcTemplate).update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "ACTIVE", 0L, 9L);
    }

    @Test
    void terminateReleasesAllContractRooms() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList("SELECT room_id FROM contract_room WHERE contract_id = ? AND deleted = 0 ORDER BY id", 9L))
                .thenReturn(List.of(Map.of("room_id", 12L), Map.of("room_id", 13L)));
        when(jdbcTemplate.update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "TERMINATED", 0L, 9L))
                .thenReturn(1);

        ApiResponse<Void> response = new ContractController(jdbcTemplate).terminate(9L, Map.of("reason", "test"));

        assertEquals(200, response.code());
        verify(jdbcTemplate).update("UPDATE building_room SET rent_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "VACANT", 0L, 12L);
        verify(jdbcTemplate).update("UPDATE building_room SET rent_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "VACANT", 0L, 13L);
        verify(jdbcTemplate).update("UPDATE contract SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0", "TERMINATED", 0L, 9L);
    }
}
