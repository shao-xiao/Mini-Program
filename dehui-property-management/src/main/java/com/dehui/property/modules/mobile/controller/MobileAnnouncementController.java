package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.announcement.entity.Announcement;
import com.dehui.property.modules.announcement.repository.AnnouncementRepository;
import com.dehui.property.modules.mobile.dto.MobileAnnouncementResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/mobile/announcements")
@RequiredArgsConstructor
public class MobileAnnouncementController {

    private final AnnouncementRepository announcementRepository;

    @GetMapping
    public Result<List<MobileAnnouncementResponse>> list() {
        List<MobileAnnouncementResponse> responses = announcementRepository.findByStatusOrderByPublishTimeDesc("PUBLISHED")
                .stream()
                .map(this::toResponse)
                .toList();
        return Result.success(responses);
    }

    @GetMapping("/{id}")
    public Result<MobileAnnouncementResponse> detail(@PathVariable Long id) {
        return announcementRepository.findById(id)
                .filter(item -> "PUBLISHED".equals(item.getStatus()))
                .map(item -> Result.success(toResponse(item)))
                .orElseGet(() -> Result.error("公告不存在或未发布"));
    }

    private MobileAnnouncementResponse toResponse(Announcement announcement) {
        MobileAnnouncementResponse response = new MobileAnnouncementResponse();
        response.setId(announcement.getId());
        response.setTitle(announcement.getTitle());
        response.setContent(announcement.getContent());
        response.setType(announcement.getType());
        response.setPublishTime(announcement.getPublishTime());
        return response;
    }
}
