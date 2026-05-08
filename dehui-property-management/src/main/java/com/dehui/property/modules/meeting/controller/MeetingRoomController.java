package com.dehui.property.modules.meeting.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.meeting.dto.MeetingRoomRequest;
import com.dehui.property.modules.meeting.dto.MeetingRoomResponse;
import com.dehui.property.modules.meeting.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/meetings/rooms")
@RequiredArgsConstructor
public class MeetingRoomController {

    private final MeetingService meetingService;

    @GetMapping
    public Result<List<MeetingRoomResponse>> list() {
        return Result.success(meetingService.listRooms());
    }

    @GetMapping("/availability")
    public Result<List<MeetingRoomResponse>> availability(
            @RequestParam LocalDateTime startTime,
            @RequestParam LocalDateTime endTime) {
        return Result.success(meetingService.listAvailableRooms(startTime, endTime));
    }

    @PostMapping
    public Result<MeetingRoomResponse> create(@Valid @RequestBody MeetingRoomRequest request) {
        return Result.success(meetingService.createRoom(request));
    }

    @PutMapping("/{id}")
    public Result<MeetingRoomResponse> update(@PathVariable Long id, @Valid @RequestBody MeetingRoomRequest request) {
        return Result.success(meetingService.updateRoom(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        meetingService.deleteRoom(id);
        return Result.success();
    }
}
