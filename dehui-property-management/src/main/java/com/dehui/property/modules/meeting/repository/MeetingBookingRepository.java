package com.dehui.property.modules.meeting.repository;

import com.dehui.property.modules.meeting.entity.MeetingBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MeetingBookingRepository extends JpaRepository<MeetingBooking, Long> {
    List<MeetingBooking> findByMeetingRoomIdOrderByStartTimeDesc(Long meetingRoomId);

    List<MeetingBooking> findByTenantIdOrderByStartTimeDesc(Long tenantId);

    List<MeetingBooking> findByInternalUserIdOrderByStartTimeDesc(Long internalUserId);

    boolean existsByMeetingRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long meetingRoomId,
            List<String> statuses,
            LocalDateTime endTime,
            LocalDateTime startTime
    );
}
