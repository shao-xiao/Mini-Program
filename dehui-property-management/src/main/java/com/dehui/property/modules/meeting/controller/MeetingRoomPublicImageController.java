package com.dehui.property.modules.meeting.controller;

import com.dehui.property.modules.meeting.application.MeetingRoomImageService;
import java.time.Duration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeetingRoomPublicImageController {

    private final MeetingRoomImageService meetingRoomImageService;

    public MeetingRoomPublicImageController(MeetingRoomImageService meetingRoomImageService) {
        this.meetingRoomImageService = meetingRoomImageService;
    }

    @GetMapping("/mobile/public/meeting-rooms/{roomId}/images/{fileId}")
    public ResponseEntity<Resource> image(
            @PathVariable Long roomId,
            @PathVariable Long fileId) {
        MeetingRoomImageService.PublicImage image = meetingRoomImageService.requirePublicImage(roomId, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.mimeType()))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(image.resource());
    }
}
