package com.dehui.property.modules.announcement.repository;

import com.dehui.property.modules.announcement.entity.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findByStatusOrderByPublishTimeDesc(String status);
}