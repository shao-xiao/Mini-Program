package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.meeting.dto.MeetingBookingResponse;
import com.dehui.property.modules.mobile.dto.MobileMeetingBookingRequest;
import com.dehui.property.modules.mobile.dto.MobileMeetingHomeResponse;
import com.dehui.property.modules.mobile.service.MobileMeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/mobile/meetings")
@RequiredArgsConstructor
public class MobileMeetingController {

    private final MobileMeetingService mobileMeetingService;

    @GetMapping
    public Result<MobileMeetingHomeResponse> home(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        return mobileMeetingService.home(normalizeToken(token), startTime, endTime);
    }

    @PostMapping("/bookings")
    public Result<MeetingBookingResponse> create(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MobileMeetingBookingRequest request) {
        return mobileMeetingService.create(normalizeToken(token), request);
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
