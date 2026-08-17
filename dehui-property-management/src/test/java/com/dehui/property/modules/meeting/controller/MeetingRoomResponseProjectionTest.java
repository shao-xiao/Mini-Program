package com.dehui.property.modules.meeting.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.PageResponse;
import com.dehui.property.config.FileStorageProperties;
import com.dehui.property.modules.asset.application.AssetChargeBasisService;
import com.dehui.property.modules.meeting.application.MeetingRoomImageService;
import com.dehui.property.modules.mobile.service.MobileIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class MeetingRoomResponseProjectionTest {

    @TempDir
    Path uploadRoot;

    @Test
    void publicRoomsExposeCompatibleDetailsAndImagesWithoutPricing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 3L,
                "roomId", 3L,
                "name", "8F会议室",
                "roomName", "8F会议室",
                "imageFileIds", "[{\"fileId\":24}]",
                "available", 1
        )));
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(Map.of(
                "fileId", 24L,
                "originalName", "会议室.png",
                "mimeType", "image/png",
                "fileSize", 3175325L
        )));
        MeetingRoomImageService imageService = new MeetingRoomImageService(
                jdbcTemplate,
                new ObjectMapper(),
                new FileStorageProperties(uploadRoot.toString())
        );
        MeetingController controller = new MeetingController(
                jdbcTemplate,
                mock(AssetChargeBasisService.class),
                null,
                null,
                mock(MobileIdentityService.class),
                imageService
        );

        ApiResponse<PageResponse<Map<String, Object>>> response = controller.publicMeetingRooms(
                "2026-07-28T10:00:00",
                "2026-07-28T11:00:00",
                1,
                20
        );

        Map<String, Object> room = response.data().records().getFirst();
        assertEquals(3L, room.get("roomId"));
        assertEquals("8F会议室", room.get("roomName"));
        assertFalse(room.containsKey("imageFileIds"));
        assertEquals("/api/mobile/public/meeting-rooms/3/images/24", room.get("coverImageUrl"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) room.get("images");
        assertEquals(1, images.size());
        assertEquals(Map.of(
                "url",
                "/api/mobile/public/meeting-rooms/3/images/24"
        ), images.getFirst());
        assertFalse(room.containsKey("workdayHourlyRate"));
        assertFalse(room.containsKey("workdayWorkHourRate"));
        assertFalse(room.containsKey("chargeType"));

        ArgumentCaptor<String> roomSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(roomSql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(roomSql.getValue().contains("room_record.id AS roomId"));
        assertTrue(roomSql.getValue().contains("room_record.name AS roomName"));
        assertFalse(roomSql.getValue().contains("hourly_price"));
    }

    @Test
    void publicRoomsReturnEmptyImageProjectionForMalformedLegacyJson() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(Map.of(
                "id", 4L,
                "name", "无图会议室",
                "imageFileIds", "not-json",
                "available", 1
        )));
        MeetingRoomImageService imageService = new MeetingRoomImageService(
                jdbcTemplate,
                new ObjectMapper(),
                new FileStorageProperties(uploadRoot.toString())
        );
        MeetingController controller = new MeetingController(
                jdbcTemplate,
                mock(AssetChargeBasisService.class),
                null,
                null,
                mock(MobileIdentityService.class),
                imageService
        );

        Map<String, Object> room = controller.publicMeetingRooms(null, null, 1, 20)
                .data()
                .records()
                .getFirst();

        assertEquals(4L, room.get("roomId"));
        assertEquals("无图会议室", room.get("roomName"));
        assertEquals(List.of(), room.get("images"));
        assertNull(room.get("coverImageUrl"));
    }
}
