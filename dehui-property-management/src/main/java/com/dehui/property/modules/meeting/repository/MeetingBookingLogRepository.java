package com.dehui.property.modules.meeting.repository;

import com.dehui.property.modules.meeting.entity.MeetingBookingLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MeetingBookingLogRepository extends JpaRepository<MeetingBookingLog, Long> {
    List<MeetingBookingLog> findByBookingIdOrderByCreatedTimeDesc(Long bookingId);
}
