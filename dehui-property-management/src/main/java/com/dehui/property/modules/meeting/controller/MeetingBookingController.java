package com.dehui.property.modules.meeting.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.meeting.dto.InternalApplicantResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingCalculateResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingCancelRequest;
import com.dehui.property.modules.meeting.dto.MeetingBookingLogResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingRequest;
import com.dehui.property.modules.meeting.dto.MeetingBookingResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingStatsResponse;
import com.dehui.property.modules.meeting.service.MeetingService;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.service.SystemUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping({"/meeting-bookings", "/meetings/bookings"})
@RequiredArgsConstructor
public class MeetingBookingController {

    private final MeetingService meetingService;
    private final SystemUserService systemUserService;

    @GetMapping
    public Result<List<MeetingBookingResponse>> list(
            @RequestParam(required = false) String bookingNo,
            @RequestParam(required = false) Long roomId,
            @RequestParam(required = false) Long meetingRoomId,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String applicantName,
            @RequestParam(required = false) String tenantName,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        return Result.success(meetingService.listBookings(
                bookingNo,
                roomId == null ? meetingRoomId : roomId,
                sourceType,
                status,
                applicantName,
                tenantName,
                department,
                startDate,
                endDate
        ));
    }

    @GetMapping("/stats")
    public Result<MeetingBookingStatsResponse> stats() {
        return Result.success(meetingService.stats());
    }

    @GetMapping("/calculate")
    public Result<MeetingBookingCalculateResponse> calculate(
            @RequestParam Long roomId,
            @RequestParam String sourceType,
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime) {
        return meetingService.calculate(roomId, sourceType, startTime, endTime);
    }

    @GetMapping("/internal-applicants")
    public Result<List<InternalApplicantResponse>> internalApplicants() {
        return Result.success(meetingService.listInternalApplicants());
    }

    @GetMapping("/{id}")
    public Result<MeetingBookingResponse> detail(@PathVariable Long id) {
        return meetingService.getBooking(id);
    }

    @GetMapping("/{id}/logs")
    public Result<List<MeetingBookingLogResponse>> logs(@PathVariable Long id) {
        return meetingService.logs(id);
    }

    @PostMapping
    public Result<MeetingBookingResponse> create(
            @RequestHeader(value = "Authorization", required = false) String token,
            @Valid @RequestBody MeetingBookingRequest request) {
        return meetingService.createBooking(request, currentUser(token));
    }

    @PutMapping("/{id}")
    public Result<MeetingBookingResponse> update(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id,
            @Valid @RequestBody MeetingBookingRequest request) {
        return meetingService.updateBooking(id, request, currentUser(token));
    }

    @RequestMapping(value = "/{id}/confirm", method = {RequestMethod.POST, RequestMethod.PATCH})
    public Result<MeetingBookingResponse> confirm(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        SysUser user = currentUser(token);
        return meetingService.confirmBooking(id, user == null ? "system" : user.getUsername());
    }

    @RequestMapping(value = "/{id}/cancel", method = {RequestMethod.POST, RequestMethod.PATCH})
    public Result<MeetingBookingResponse> cancel(@PathVariable Long id,
                                                 @RequestBody(required = false) MeetingBookingCancelRequest request) {
        return meetingService.cancelBooking(id, request);
    }

    @PostMapping("/{id}/complete")
    public Result<MeetingBookingResponse> complete(
            @RequestHeader(value = "Authorization", required = false) String token,
            @PathVariable Long id) {
        SysUser user = currentUser(token);
        return meetingService.completeBooking(id, user == null ? "system" : user.getUsername());
    }

    private SysUser currentUser(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return token == null || token.isBlank() ? null : systemUserService.getByToken(token);
    }
}
