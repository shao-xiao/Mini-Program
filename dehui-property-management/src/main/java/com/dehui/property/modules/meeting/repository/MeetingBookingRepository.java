package com.dehui.property.modules.meeting.repository;

import com.dehui.property.modules.meeting.entity.MeetingBooking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface MeetingBookingRepository extends JpaRepository<MeetingBooking, Long> {
    List<MeetingBooking> findByMeetingRoomIdOrderByStartTimeDesc(Long meetingRoomId);

    List<MeetingBooking> findByTenantIdOrderByStartTimeDesc(Long tenantId);

    long countByTenantId(Long tenantId);

    List<MeetingBooking> findByInternalUserIdOrderByStartTimeDesc(Long internalUserId);

    boolean existsByMeetingRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
            Long meetingRoomId,
            List<String> statuses,
            LocalDateTime endTime,
            LocalDateTime startTime
    );

    boolean existsByMeetingRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
            Long meetingRoomId,
            List<String> statuses,
            LocalDateTime endTime,
            LocalDateTime startTime,
            Long id
    );

    @Query("select b from MeetingBooking b where b.startTime >= :start and b.startTime < :end")
    List<MeetingBooking> findByStartTimeBetweenOpen(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select b from MeetingBooking b where (:bookingNo is null or b.bookingNo like concat('%', :bookingNo, '%') or b.bookingNumber like concat('%', :bookingNo, '%'))"
            + " and (:roomId is null or b.meetingRoomId = :roomId or b.roomId = :roomId)"
            + " and (:sourceType is null or b.sourceType = :sourceType or b.applicantType = :sourceType)"
            + " and (:status is null or b.status = :status)"
            + " and (:applicantName is null or b.applicantName like concat('%', :applicantName, '%'))"
            + " and (:tenantName is null or b.tenantName like concat('%', :tenantName, '%'))"
            + " and (:department is null or b.department like concat('%', :department, '%') or b.departmentName like concat('%', :department, '%'))"
            + " and (:startTime is null or b.startTime >= :startTime)"
            + " and (:endTime is null or b.startTime < :endTime)")
    List<MeetingBooking> search(
            @Param("bookingNo") String bookingNo,
            @Param("roomId") Long roomId,
            @Param("sourceType") String sourceType,
            @Param("status") String status,
            @Param("applicantName") String applicantName,
            @Param("tenantName") String tenantName,
            @Param("department") String department,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    @Query("select count(b) from MeetingBooking b where b.startTime >= :start and b.startTime < :end")
    long countByStartRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("select count(b) from MeetingBooking b where b.startTime >= :start and b.startTime < :end and b.sourceType = :sourceType")
    long countByStartRangeAndSourceType(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("sourceType") String sourceType);

    @Query("select count(b) from MeetingBooking b where b.startTime >= :start and b.startTime < :end and b.status = :status")
    long countByStartRangeAndStatus(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, @Param("status") String status);
}
