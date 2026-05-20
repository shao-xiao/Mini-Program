package com.dehui.property.modules.meeting.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.meeting.dto.MeetingRoomRequest;
import com.dehui.property.modules.meeting.dto.MeetingRoomResponse;
import com.dehui.property.modules.meeting.service.MeetingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping({"/meeting-rooms", "/meetings/rooms"})
@RequiredArgsConstructor
public class MeetingRoomController {

    private final MeetingService meetingService;

    @GetMapping
    public Result<List<MeetingRoomResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer minCapacity) {
        return Result.success(meetingService.listRooms(name, status, minCapacity));
    }

    @GetMapping("/{id}")
    public Result<MeetingRoomResponse> detail(@PathVariable Long id) {
        return meetingService.getRoom(id);
    }

    @GetMapping({"/available", "/availability"})
    public Result<List<MeetingRoomResponse>> available(
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
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
        return meetingService.deleteRoom(id);
    }
}
