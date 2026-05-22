package com.dehui.property.modules.meeting.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeetingController {

    @GetMapping("/meeting-rooms")
    public ApiResponse<Void> meetingRooms() {
        throw BusinessException.notImplemented("会议室");
    }

    @GetMapping("/meeting-bookings")
    public ApiResponse<Void> meetingBookings() {
        throw BusinessException.notImplemented("会议预约");
    }

    @GetMapping("/meeting-bills")
    public ApiResponse<Void> meetingBills() {
        throw BusinessException.notImplemented("会议账单");
    }

    @GetMapping("/mobile/meeting-rooms")
    public ApiResponse<Void> mobileMeetingRooms() {
        throw BusinessException.notImplemented("移动端会议室");
    }

    @GetMapping("/mobile/meetings")
    public ApiResponse<Void> mobileMeetings() {
        throw BusinessException.notImplemented("移动端会议");
    }
}
