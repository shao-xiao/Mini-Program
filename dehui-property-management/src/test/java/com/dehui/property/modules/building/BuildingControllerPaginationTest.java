package com.dehui.property.modules.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.modules.building.controller.BuildingController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class BuildingControllerPaginationTest {

    @Test
    void roomsReturnBackendPageShape() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(12L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(
                Map.of("id", 11L, "roomNumber", "1101"),
                Map.of("id", 12L, "roomNumber", "1102")
        ));

        ApiResponse<PageResponse<Map<String, Object>>> response = new BuildingController(jdbcTemplate)
                .rooms(1L, 2L, "VACANT", 2, 10);

        assertEquals(200, response.code());
        assertEquals(12L, response.data().total());
        assertEquals(2, response.data().page());
        assertEquals(10, response.data().pageSize());
        assertEquals(2, response.data().records().size());
        assertEquals("1101", response.data().records().getFirst().get("roomNumber"));

        verify(jdbcTemplate).query(
                eq("""
                SELECT r.id,
                       r.code AS roomCode,
                       r.building_id AS buildingId,
                       b.name AS buildingName,
                       r.floor_id AS floorId,
                       f.name AS floorName,
                       r.room_no AS roomNumber,
                       r.room_no AS roomName,
                       r.area,
                       r.usage_type AS roomType,
                       r.rent_status AS status,
                       r.remark AS description
                FROM building_room r
                LEFT JOIN building b ON b.id = r.building_id
                LEFT JOIN building_floor f ON f.id = r.floor_id
                WHERE r.deleted = 0
                 AND r.building_id = ? AND r.floor_id = ? AND r.rent_status = ? ORDER BY r.room_no ASC, r.id ASC LIMIT ? OFFSET ?"""),
                any(RowMapper.class),
                eq(1L),
                eq(2L),
                eq("VACANT"),
                eq(10),
                eq(10)
        );
    }
}
