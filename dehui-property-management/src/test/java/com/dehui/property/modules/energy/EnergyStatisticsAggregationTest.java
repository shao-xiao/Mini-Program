package com.dehui.property.modules.energy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.dehui.property.modules.energy.controller.EnergyController;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EnergyStatisticsAggregationTest {
    @Test void returnsRealTrendAndRankingsWithEveryFilterBoundToTheSameScope() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> queries = new ArrayList<>();
        List<List<Object>> arguments = new ArrayList<>();
        Map<String, Object> month = Map.of("periodMonth", "2026-09", "recordCount", 10L,
                "electricUsage", new BigDecimal("1479.29"), "electricRecordCount", 10L);
        Map<String, Object> room = Map.of("roomId", 7L, "roomName", "701", "meterType", "ELECTRIC",
                "usageAmount", new BigDecimal("1479.29"));
        Map<String, Object> building = Map.of("buildingId", 2L, "buildingName", "A座", "meterType", "ELECTRIC",
                "usageAmount", new BigDecimal("1479.29"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            queries.add(sql);
            arguments.add(Arrays.asList((Object[]) invocation.getRawArguments()[1]));
            if (sql.contains("GROUP BY er.period_month")) return List.of(month);
            if (sql.contains("GROUP BY m.room_id")) return List.of(room);
            if (sql.contains("GROUP BY m.building_id")) return List.of(building);
            if (sql.contains("GROUP BY m.meter_type")) return List.of(Map.of(
                    "meterType", "ELECTRIC", "recordCount", 10L, "abnormalCount", 0L,
                    "usageAmount", new BigDecimal("1479.29"), "settlementAmount", new BigDecimal("1937.88")));
            return List.of();
        });
        Map<String, Object> data = new EnergyController(jdbc).stats("ELECTRIC", "2026-09", 2L, 3L,
                7L, 9L, null, "GENERATED", false).data();
        assertEquals(List.of(month), data.get("trend"));
        assertEquals(List.of(room), data.get("roomRanking"));
        assertEquals(List.of(building), data.get("buildingRanking"));
        assertEquals(new BigDecimal("1937.88"), data.get("totalAmount"));
        assertEquals(5, queries.size());
        for (int i = 0; i < queries.size(); i++) {
            assertTrue(queries.get(i).contains("er.deleted = 0"));
            assertTrue(queries.get(i).contains("m.deleted = 0"));
            assertTrue(queries.get(i).contains("m.tenant_id = ?"));
            assertEquals(List.of("ELECTRIC", "2026-09", "GENERATED", 0, 2L, 3L, 7L, 9L), arguments.get(i));
        }
        assertTrue(queries.stream().filter(q -> q.contains("GROUP BY m.room_id")).allMatch(q -> q.contains("m.meter_type")));
        assertTrue(queries.stream().anyMatch(q -> q.contains("electricRecordCount") && q.contains("waterRecordCount")));
    }

    @Test void propertyScopeIsAppliedToEveryAggregateAndAnomalyQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        List<String> queries = new ArrayList<>();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            queries.add(invocation.getArgument(0));
            return List.of();
        });
        Map<String, Object> data = new EnergyController(jdbc).stats(null, null, null, null,
                null, null, "PROPERTY", null, null).data();
        assertEquals(5, queries.size());
        assertTrue(queries.stream().allMatch(sql -> sql.contains("m.tenant_id IS NULL")));
        assertEquals(List.of(), data.get("trend"));
        assertEquals(List.of(), data.get("roomRanking"));
    }
}
