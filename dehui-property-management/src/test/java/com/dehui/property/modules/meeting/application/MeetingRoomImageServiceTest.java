package com.dehui.property.modules.meeting.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dehui.property.common.BusinessException;
import com.dehui.property.config.FileStorageProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class MeetingRoomImageServiceTest {

    @TempDir
    Path uploadRoot;

    @Test
    void decoratesLegacyJsonWithStableAliasesAndPublicUrls() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenReturn(List.of(
                fileRow(24L, "第一张.png", "image/png", 1200L, null),
                fileRow(25L, "封面.webp", "image/webp", 800L, null),
                fileRow(26L, "伪装.svg", "image/svg+xml", 100L, null)
        ));
        MeetingRoomImageService service = service(jdbcTemplate);
        String original = """
                [
                  {"id":24,"sortOrder":1},
                  {"fileId":25,"sortOrder":0},
                  26,
                  {"fileId":24,"sortOrder":9}
                ]
                """;

        List<Map<String, Object>> result = service.decorateRooms(List.of(Map.of(
                "id", 3L,
                "name", "8F会议室",
                "imageFileIds", original
        )));

        Map<String, Object> room = result.getFirst();
        assertEquals(3L, room.get("roomId"));
        assertEquals("8F会议室", room.get("roomName"));
        assertEquals(original, room.get("imageFileIds"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) room.get("images");
        assertEquals(2, images.size());
        assertEquals(25L, images.getFirst().get("fileId"));
        assertEquals("/api/mobile/public/meeting-rooms/3/images/25", images.getFirst().get("url"));
        assertEquals("/api/mobile/public/meeting-rooms/3/images/25", room.get("coverImageUrl"));
        assertEquals(24L, images.get(1).get("fileId"));
        ArgumentCaptor<String> metadataSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(metadataSql.capture(), any(Object[].class));
        assertTrue(metadataSql.getValue().contains("workspace_key = 'meeting-rooms'"));
        assertTrue(metadataSql.getValue().contains("access_scope = 'INTERNAL'"));
    }

    @Test
    void streamsOnlyAnEnabledRoomImageReferencedByTheRoom() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        Path stored = uploadRoot.resolve("private/custom-fields/room.png");
        Files.createDirectories(stored.getParent());
        Files.write(stored, new byte[]{1, 2, 3});
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM meeting_room")) {
                return List.of(Map.of("imageFileIds", "[{\"fileId\":24}]"));
            }
            return List.of(fileRow(
                    24L,
                    "会议室.png",
                    "image/png",
                    3L,
                    "private/custom-fields/room.png"
            ));
        });
        MeetingRoomImageService service = service(jdbcTemplate);

        MeetingRoomImageService.PublicImage image = service.requirePublicImage(3L, 24L);

        assertEquals("image/png", image.mimeType());
        assertNotNull(image.resource());
        assertTrue(image.resource().exists());
    }

    @Test
    void rejectsAFileThatIsNotReferencedByTheEnabledRoom() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class)))
                .thenReturn(List.of(Map.of("imageFileIds", "[25]")));
        MeetingRoomImageService service = service(jdbcTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.requirePublicImage(3L, 24L)
        );

        assertEquals(404, exception.getCode());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("FROM meeting_room"));
        assertFalse(sql.getValue().contains("FROM file_upload"));
    }

    @Test
    void rejectsNonImageOrWrongWorkspaceMetadata() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM meeting_room")) {
                return List.of(Map.of("imageFileIds", "[24]"));
            }
            return List.of();
        });
        MeetingRoomImageService service = service(jdbcTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.requirePublicImage(3L, 24L)
        );

        assertEquals(404, exception.getCode());
    }

    @Test
    void rejectsStoragePathsThatEscapeTheConfiguredUploadRoot() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FROM meeting_room")) {
                return List.of(Map.of("imageFileIds", "{\"files\":[{\"id\":24}]}"));
            }
            return List.of(fileRow(
                    24L,
                    "会议室.png",
                    "image/png",
                    3L,
                    "../outside.png"
            ));
        });
        MeetingRoomImageService service = service(jdbcTemplate);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.requirePublicImage(3L, 24L)
        );

        assertEquals(404, exception.getCode());
    }

    private MeetingRoomImageService service(JdbcTemplate jdbcTemplate) {
        return new MeetingRoomImageService(
                jdbcTemplate,
                new ObjectMapper(),
                new FileStorageProperties(uploadRoot.toString())
        );
    }

    private Map<String, Object> fileRow(
            Long fileId,
            String originalName,
            String mimeType,
            Long fileSize,
            String storagePath) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("fileId", fileId);
        row.put("originalName", originalName);
        row.put("mimeType", mimeType);
        row.put("fileSize", fileSize);
        row.put("storagePath", storagePath);
        return row;
    }
}
