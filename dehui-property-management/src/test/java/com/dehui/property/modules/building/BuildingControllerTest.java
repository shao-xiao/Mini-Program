package com.dehui.property.modules.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.modules.building.controller.BuildingController;
import com.dehui.property.modules.building.controller.BuildingController.BuildingRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class BuildingControllerTest {

    @Test
    void buildingsReturnsRowsForAdminList() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(BuildingController.BUILDING_LIST_SQL))
                .thenReturn(List.of(Map.of(
                        "id", 1L,
                        "buildingName", "Dehui",
                        "buildingCode", "BLD001",
                        "status", "ACTIVE"
                )));

        ApiResponse<List<Map<String, Object>>> response = new BuildingController(jdbcTemplate).buildings();

        assertEquals(200, response.code());
        assertEquals("Dehui", response.data().getFirst().get("buildingName"));
    }

    @Test
    void createBuildingWritesToMysql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        BuildingRequest request = new BuildingRequest("", "Dehui", "Shanghai", 3, "ACTIVE", "test");
        ApiResponse<Void> response = new BuildingController(jdbcTemplate).createBuilding(request);

        assertEquals(200, response.code());
        verify(jdbcTemplate).update(
                eq(BuildingController.INSERT_BUILDING_SQL),
                any(), eq("Dehui"), eq("Shanghai"), eq(3), eq("ACTIVE"), eq(0L), eq(0L), eq("test")
        );
    }

    @Test
    void blankBuildingNameIsRejected() {
        BuildingController controller = new BuildingController(mock(JdbcTemplate.class));
        BuildingRequest request = new BuildingRequest("", " ", "", 1, "ACTIVE", "");

        try {
            controller.createBuilding(request);
        } catch (Exception exception) {
            assertEquals("Building name is required", exception.getMessage());
            return;
        }

        assertFalse(true, "blank building name should fail");
    }
}
