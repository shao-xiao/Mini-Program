package com.dehui.property.modules.meeting.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.JdbcMaps;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MeetingController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping({"/meeting-rooms", "/mobile/meeting-rooms", "/meeting-rooms/available"})
    public ApiResponse<List<Map<String, Object>>> meetingRooms() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT id, code, name, capacity, location, hourly_price AS workdayHourlyRate,
                       hourly_price AS offHourHourlyRate, hourly_price AS holidayHourlyRate, status, remark
                FROM meeting_room
                WHERE deleted = 0
                ORDER BY id DESC
                """
        ));
    }

    @PostMapping("/meeting-rooms")
    public ApiResponse<Void> createMeetingRoom(@RequestBody Map<String, Object> body) {
        saveMeetingRoom(null, body);
        return ApiResponse.success();
    }

    @PutMapping("/meeting-rooms/{id}")
    public ApiResponse<Void> updateMeetingRoom(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        saveMeetingRoom(id, body);
        return ApiResponse.success();
    }

    @DeleteMapping("/meeting-rooms/{id}")
    public ApiResponse<Void> deleteMeetingRoom(@PathVariable Long id) {
        ensureUpdated(jdbcTemplate.update("UPDATE meeting_room SET deleted = 1, updated_by = ? WHERE id = ? AND deleted = 0", 0L, id), "Meeting room not found");
        return ApiResponse.success();
    }

    @GetMapping({"/meeting-bookings", "/mobile/meetings"})
    public ApiResponse<List<Map<String, Object>>> meetingBookings() {
        return ApiResponse.success(jdbcTemplate.queryForList(
                """
                SELECT b.id, b.code AS bookingNo, b.meeting_room_id AS roomId, r.name AS roomName,
                       b.tenant_id AS tenantId, b.start_time AS startTime, b.end_time AS endTime,
                       b.booking_status AS status, b.remark
                FROM meeting_booking b
                LEFT JOIN meeting_room r ON r.id = b.meeting_room_id
                WHERE b.deleted = 0
                ORDER BY b.start_time DESC, b.id DESC
                """
        ));
    }

    @GetMapping("/meeting-bookings/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.success(Map.of("todayBookingCount", 0, "monthBookingCount", 0, "monthRevenue", 0, "cancelRate", 0));
    }

    @GetMapping("/meeting-bookings/calculate")
    public ApiResponse<Map<String, Object>> calculate() {
        return ApiResponse.success(Map.of("feeType", "HOURLY", "amount", 0));
    }

    @PostMapping("/meeting-bookings")
    public ApiResponse<Void> createBooking(@RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO meeting_booking (code, meeting_room_id, tenant_id, start_time, end_time, booking_status, status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, 'PENDING', 'ACTIVE', ?, ?, ?)
                """,
                JdbcMaps.code("MBK"),
                JdbcMaps.requiredLong(body, "Meeting room is required", "roomId", "meetingRoomId"),
                JdbcMaps.longVal(body, "tenantId"),
                JdbcMaps.datetime(body, JdbcMaps.now(), "startTime"),
                JdbcMaps.datetime(body, JdbcMaps.now(), "endTime"),
                0L,
                0L,
                JdbcMaps.str(body, "remark", "applicantName", "applicantPhone")
        );
        return ApiResponse.success();
    }

    @PostMapping("/meeting-bookings/{id}/confirm")
    public ApiResponse<Void> confirm(@PathVariable Long id) {
        updateBooking(id, "CONFIRMED");
        return ApiResponse.success();
    }

    @PostMapping("/meeting-bookings/{id}/cancel")
    public ApiResponse<Void> cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        ensureUpdated(jdbcTemplate.update("UPDATE meeting_booking SET booking_status = 'CANCELLED', remark = COALESCE(?, remark), updated_by = ? WHERE id = ? AND deleted = 0",
                JdbcMaps.str(body, "cancelReason", "remark"), 0L, id), "Booking not found");
        return ApiResponse.success();
    }

    @PostMapping("/meeting-bookings/{id}/complete")
    public ApiResponse<Void> complete(@PathVariable Long id) {
        updateBooking(id, "COMPLETED");
        return ApiResponse.success();
    }

    @GetMapping("/meeting-bills")
    public ApiResponse<List<Map<String, Object>>> meetingBills() {
        return ApiResponse.success(jdbcTemplate.queryForList("SELECT * FROM meeting_bill WHERE deleted = 0 ORDER BY id DESC"));
    }

    private void saveMeetingRoom(Long id, Map<String, Object> body) {
        String name = JdbcMaps.requiredStr(body, "Meeting room name is required", "name");
        BigDecimal price = JdbcMaps.decimal(body, BigDecimal.ZERO, "workdayHourlyRate", "hourlyPrice");
        if (id == null) {
            jdbcTemplate.update(
                    """
                    INSERT INTO meeting_room (code, name, capacity, location, hourly_price, status, created_by, updated_by, remark)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    JdbcMaps.code("MR"),
                    name,
                    JdbcMaps.intVal(body, 0, "capacity"),
                    JdbcMaps.str(body, "location"),
                    price,
                    JdbcMaps.strOr(body, "AVAILABLE", "status"),
                    0L,
                    0L,
                    JdbcMaps.str(body, "remark", "equipment")
            );
            return;
        }
        ensureUpdated(jdbcTemplate.update(
                """
                UPDATE meeting_room
                SET name = ?, capacity = ?, location = ?, hourly_price = ?, status = ?, updated_by = ?, remark = ?
                WHERE id = ? AND deleted = 0
                """,
                name,
                JdbcMaps.intVal(body, 0, "capacity"),
                JdbcMaps.str(body, "location"),
                price,
                JdbcMaps.strOr(body, "AVAILABLE", "status"),
                0L,
                JdbcMaps.str(body, "remark", "equipment"),
                id
        ), "Meeting room not found");
    }

    private void updateBooking(Long id, String status) {
        ensureUpdated(jdbcTemplate.update("UPDATE meeting_booking SET booking_status = ?, updated_by = ? WHERE id = ? AND deleted = 0", status, 0L, id), "Booking not found");
    }

    private void ensureUpdated(int updated, String message) {
        if (updated == 0) {
            throw new BusinessException(404, message);
        }
    }
}
