package com.dehui.property.modules.meeting.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.config.FileStorageProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MeetingRoomImageService {

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final FileStorageProperties fileStorageProperties;

    public MeetingRoomImageService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            FileStorageProperties fileStorageProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.fileStorageProperties = fileStorageProperties;
    }

    public List<Map<String, Object>> decorateRooms(List<Map<String, Object>> rooms) {
        if (rooms == null || rooms.isEmpty()) {
            return List.of();
        }

        List<RoomImages> roomImages = rooms.stream()
                .map(room -> new RoomImages(
                        copy(room),
                        longValue(room.get("roomId"), room.get("id")),
                        imageReferences(room.get("imageFileIds"))
                ))
                .toList();
        Set<Long> requestedFileIds = new LinkedHashSet<>();
        roomImages.forEach(room -> room.references().forEach(reference -> requestedFileIds.add(reference.fileId())));
        Map<Long, FileMetadata> metadata = loadMetadata(requestedFileIds);

        List<Map<String, Object>> result = new ArrayList<>(roomImages.size());
        for (RoomImages room : roomImages) {
            Map<String, Object> decorated = room.room();
            decorated.putIfAbsent("roomId", room.roomId());
            decorated.putIfAbsent("roomName", decorated.get("name"));
            List<Map<String, Object>> images = new ArrayList<>();
            if (room.roomId() != null) {
                for (ImageReference reference : room.references()) {
                    FileMetadata file = metadata.get(reference.fileId());
                    if (file == null) continue;
                    Map<String, Object> image = new LinkedHashMap<>();
                    image.put("fileId", file.fileId());
                    image.put("id", file.fileId());
                    image.put("name", file.originalName());
                    image.put("mimeType", file.mimeType());
                    image.put("size", file.fileSize());
                    image.put("sortOrder", reference.sortOrder());
                    image.put("url", publicImageUrl(room.roomId(), file.fileId()));
                    images.add(image);
                }
            }
            decorated.put("images", images);
            decorated.put("coverImageUrl", images.isEmpty() ? null : images.getFirst().get("url"));
            result.add(decorated);
        }
        return result;
    }

    public List<Map<String, Object>> decoratePublicRooms(List<Map<String, Object>> rooms) {
        return decorateRooms(rooms).stream().map(room -> {
            Map<String, Object> publicRoom = new LinkedHashMap<>(room);
            publicRoom.remove("imageFileIds");
            List<Map<String, Object>> publicImages = new ArrayList<>();
            Object rawImages = publicRoom.get("images");
            if (rawImages instanceof List<?> images) {
                for (Object rawImage : images) {
                    if (!(rawImage instanceof Map<?, ?> image)) continue;
                    String url = text(image.get("url"));
                    if (url != null && !url.isBlank()) {
                        publicImages.add(Map.of("url", url));
                    }
                }
            }
            publicRoom.put("images", publicImages);
            publicRoom.put(
                    "coverImageUrl",
                    publicImages.isEmpty() ? null : publicImages.getFirst().get("url")
            );
            return publicRoom;
        }).toList();
    }

    public PublicImage requirePublicImage(Long roomId, Long fileId) {
        List<Map<String, Object>> rooms = jdbcTemplate.queryForList(
                """
                SELECT image_file_ids AS imageFileIds
                FROM meeting_room
                WHERE id = ?
                  AND deleted = 0
                  AND status IN ('ACTIVE', 'ENABLED', 'AVAILABLE')
                LIMIT 1
                """,
                roomId
        );
        if (rooms.isEmpty() || imageReferences(rooms.getFirst().get("imageFileIds")).stream()
                .noneMatch(reference -> reference.fileId().equals(fileId))) {
            throw new BusinessException(404, "Meeting room image not found");
        }

        List<Map<String, Object>> files = jdbcTemplate.queryForList(
                """
                SELECT id AS fileId,
                       storage_path AS storagePath,
                       mime_type AS mimeType
                FROM file_upload
                WHERE id = ?
                  AND workspace_key = 'meeting-rooms'
                  AND biz_type IN ('CUSTOM_FIELD_IMAGE', 'MEETING_ROOM_IMAGE')
                  AND access_scope = 'INTERNAL'
                  AND status = 'ENABLED'
                  AND deleted = 0
                LIMIT 1
                """,
                fileId
        );
        if (files.isEmpty()) {
            throw new BusinessException(404, "Meeting room image not found");
        }
        Map<String, Object> file = files.getFirst();
        String mimeType = text(file.get("mimeType"));
        if (!IMAGE_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(404, "Meeting room image not found");
        }

        Path uploadRoot = uploadRoot();
        Path target = resolveStoragePath(uploadRoot, text(file.get("storagePath")));
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(404, "Meeting room image not found");
        }
        try {
            Path realUploadRoot = uploadRoot.toRealPath();
            target = target.toRealPath();
            if (!target.startsWith(realUploadRoot)) {
                throw new BusinessException(404, "Meeting room image not found");
            }
        } catch (IOException exception) {
            throw new BusinessException(404, "Meeting room image not found");
        }
        return new PublicImage(new FileSystemResource(target), mimeType);
    }

    List<ImageReference> imageReferences(Object rawValue) {
        Object value = parseJsonValue(rawValue);
        if (value instanceof Map<?, ?> map && map.get("files") != null) {
            value = map.get("files");
        }
        List<?> values = value instanceof List<?> list ? list : value == null ? List.of() : List.of(value);
        List<ImageReference> references = new ArrayList<>();
        for (int index = 0; index < values.size(); index++) {
            Object item = values.get(index);
            Long fileId;
            int sortOrder = index;
            if (item instanceof Map<?, ?> map) {
                fileId = longValue(map.get("fileId"), map.get("id"));
                Integer requestedOrder = integerValue(map.get("sortOrder"));
                if (requestedOrder != null && requestedOrder >= 0) {
                    sortOrder = requestedOrder;
                }
            } else {
                fileId = longValue(item);
            }
            if (fileId != null && fileId > 0) {
                references.add(new ImageReference(fileId, sortOrder, index));
            }
        }
        references.sort(Comparator.comparingInt(ImageReference::sortOrder)
                .thenComparingInt(ImageReference::sourceIndex));
        Set<Long> seen = new LinkedHashSet<>();
        return references.stream().filter(reference -> seen.add(reference.fileId())).toList();
    }

    private Map<Long, FileMetadata> loadMetadata(Set<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(fileIds.size(), "?"));
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id AS fileId,
                       original_name AS originalName,
                       mime_type AS mimeType,
                       file_size AS fileSize
                FROM file_upload
                WHERE id IN (%s)
                  AND workspace_key = 'meeting-rooms'
                  AND biz_type IN ('CUSTOM_FIELD_IMAGE', 'MEETING_ROOM_IMAGE')
                  AND access_scope = 'INTERNAL'
                  AND status = 'ENABLED'
                  AND deleted = 0
                """.formatted(placeholders),
                fileIds.toArray()
        );
        Map<Long, FileMetadata> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Long fileId = longValue(row.get("fileId"), row.get("id"));
            String mimeType = text(row.get("mimeType"));
            if (fileId == null || !IMAGE_MIME_TYPES.contains(mimeType)) continue;
            result.put(fileId, new FileMetadata(
                    fileId,
                    text(row.get("originalName")),
                    mimeType,
                    longValue(row.get("fileSize"))
            ));
        }
        return result;
    }

    private Object parseJsonValue(Object value) {
        if (!(value instanceof String text)) {
            return value;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(normalized, Object.class);
        } catch (JsonProcessingException ignored) {
            return normalized;
        }
    }

    private Path uploadRoot() {
        if (fileStorageProperties == null
                || fileStorageProperties.uploadDir() == null
                || fileStorageProperties.uploadDir().isBlank()) {
            throw new BusinessException(500, "Meeting room image storage is not configured");
        }
        return Path.of(fileStorageProperties.uploadDir()).toAbsolutePath().normalize();
    }

    private Path resolveStoragePath(Path uploadRoot, String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new BusinessException(404, "Meeting room image not found");
        }
        String relative = storagePath.replace('\\', '/');
        if (relative.startsWith("/files/")) {
            relative = relative.substring("/files/".length());
        }
        try {
            Path relativePath = Path.of(relative);
            if (relativePath.isAbsolute()) {
                throw new BusinessException(404, "Meeting room image not found");
            }
            Path target = uploadRoot.resolve(relativePath).toAbsolutePath().normalize();
            if (!target.startsWith(uploadRoot)) {
                throw new BusinessException(404, "Meeting room image not found");
            }
            return target;
        } catch (InvalidPathException exception) {
            throw new BusinessException(404, "Meeting room image not found");
        }
    }

    private String publicImageUrl(Long roomId, Long fileId) {
        return "/api/mobile/public/meeting-rooms/" + roomId + "/images/" + fileId;
    }

    private Map<String, Object> copy(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private Long longValue(Object... values) {
        for (Object value : values) {
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                try {
                    return Long.valueOf(String.valueOf(value));
                } catch (NumberFormatException ignored) {
                    // Try the next compatible representation.
                }
            }
        }
        return null;
    }

    private Integer integerValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record RoomImages(
            Map<String, Object> room,
            Long roomId,
            List<ImageReference> references) {
    }

    record ImageReference(Long fileId, int sortOrder, int sourceIndex) {
    }

    private record FileMetadata(
            Long fileId,
            String originalName,
            String mimeType,
            Long fileSize) {
    }

    public record PublicImage(Resource resource, String mimeType) {
    }
}
