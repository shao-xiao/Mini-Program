package com.dehui.property.modules.building.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.dto.RoomCreateRequest;
import com.dehui.property.modules.building.dto.RoomResponse;
import com.dehui.property.modules.building.dto.RoomUpdateRequest;
import com.dehui.property.modules.building.service.BuildingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/buildings/{buildingId}/floors/{floorId}/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final BuildingService buildingService;

    @GetMapping
    public Result<List<RoomResponse>> list(@PathVariable Long buildingId, @PathVariable Long floorId) {
        return buildingService.listRooms(buildingId, floorId);
    }

    @GetMapping("/{roomId}")
    public Result<RoomResponse> detail(@PathVariable Long buildingId,
                                       @PathVariable Long floorId,
                                       @PathVariable Long roomId) {
        return buildingService.findRoomById(buildingId, floorId, roomId);
    }

    @PostMapping
    public Result<RoomResponse> create(@PathVariable Long buildingId,
                                       @PathVariable Long floorId,
                                       @Valid @RequestBody RoomCreateRequest request) {
        return buildingService.createRoom(buildingId, floorId, request);
    }

    @PutMapping("/{roomId}")
    public Result<RoomResponse> update(@PathVariable Long buildingId,
                                       @PathVariable Long floorId,
                                       @PathVariable Long roomId,
                                       @Valid @RequestBody RoomUpdateRequest request) {
        return buildingService.updateRoom(buildingId, floorId, roomId, request);
    }

    @DeleteMapping("/{roomId}")
    public Result<Void> delete(@PathVariable Long buildingId,
                               @PathVariable Long floorId,
                               @PathVariable Long roomId) {
        return buildingService.deleteRoom(buildingId, floorId, roomId);
    }

    @PatchMapping("/{roomId}/status")
    public Result<RoomResponse> updateStatus(@PathVariable Long buildingId,
                                             @PathVariable Long floorId,
                                             @PathVariable Long roomId,
                                             @RequestParam String status) {
        return buildingService.updateRoomStatus(buildingId, floorId, roomId, status);
    }
}
