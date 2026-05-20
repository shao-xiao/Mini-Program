package com.dehui.property.modules.announcement.service;

import com.dehui.property.modules.announcement.entity.Announcement;
import com.dehui.property.modules.announcement.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public Announcement create(Announcement announcement) {
        if (announcement.getStatus() == null || announcement.getStatus().isBlank()) {
            announcement.setStatus("DRAFT");
        }
        return announcementRepository.save(announcement);
    }

    public Announcement update(Long id, Announcement request) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("公告不存在"));

        announcement.setTitle(request.getTitle());
        announcement.setType(request.getType());
        announcement.setContent(request.getContent());
        if (request.getTargetType() != null) {
            announcement.setTargetType(request.getTargetType());
            announcement.setTargetTenantId(request.getTargetTenantId());
            announcement.setTargetFloorId(request.getTargetFloorId());
            announcement.setTargetRoomId(request.getTargetRoomId());
        }
        if (request.getPinned() != null) {
            announcement.setPinned(request.getPinned());
        }
        if (request.getAttachmentUrls() != null) {
            announcement.setAttachmentUrls(request.getAttachmentUrls());
        }

        return announcementRepository.save(announcement);
    }

    public List<Announcement> list() {
        return announcementRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(
                        Announcement::getCreatedTime,
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    public List<Announcement> listPublished() {
        return announcementRepository.findByStatusOrderByPublishTimeDesc("PUBLISHED");
    }

    public Announcement publish(Long id) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("公告不存在"));

        announcement.setStatus("PUBLISHED");
        announcement.setPublishTime(LocalDateTime.now());

        return announcementRepository.save(announcement);
    }
}
