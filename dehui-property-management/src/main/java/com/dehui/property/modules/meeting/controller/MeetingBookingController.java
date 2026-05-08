package com.dehui.property.modules.meeting.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.meeting.dto.InternalApplicantResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingRequest;
import com.dehui.property.modules.meeting.dto.MeetingBookingResponse;
import com.dehui.property.modules.meeting.service.MeetingService;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.service.SystemUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/meetings/bookings")
@RequiredArgsConstructor
public class MeetingBookingController {

    private final MeetingService meetingService;
    private final SystemUserService systemUserService;

    @GetMapping
    public Result<List<MeetingBookingResponse>> list() {
        return Result.success(meetingService.listBookings());
    }

    @GetMapping("/internal-applicants")
    public Result<List<InternalApplicantResponse>> internalApplicants() {
        return Result.success(meetingService.listInternalApplicants());
    }

    @PostMapping
    public Result<MeetingBookingResponse> create(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MeetingBookingRequest request) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        SysUser currentUser = systemUserService.getByToken(token);
        if (currentUser == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        return meetingService.createBooking(request, currentUser);
    }

    @PatchMapping("/{id}/confirm")
    public Result<MeetingBookingResponse> confirm(@PathVariable Long id) {
        return meetingService.confirmBooking(id);
    }

    @PatchMapping("/{id}/cancel")
    public Result<MeetingBookingResponse> cancel(@PathVariable Long id) {
        return meetingService.cancelBooking(id);
    }
}
