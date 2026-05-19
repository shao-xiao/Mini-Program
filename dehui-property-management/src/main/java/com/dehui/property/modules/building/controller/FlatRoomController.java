package com.dehui.property.modules.building.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.building.dto.RoomCreateRequest;
import com.dehui.property.modules.building.dto.RoomResponse;
import com.dehui.property.modules.building.dto.RoomUpdateRequest;
import com.dehui.property.modules.building.service.BuildingService;
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

import java.util.List;

@RestController
@RequestMapping("/rooms")
@RequiredArgsConstructor
public class FlatRoomController {
    private final BuildingService buildingService;

    @GetMapping
    public Result<List<RoomResponse>> list(@RequestParam(name = "building_id") Long buildingId,
                                           @RequestParam(name = "floor_id", required = false) Long floorId,
                                           @RequestParam(required = false) String status) {
        return buildingService.listRooms(buildingId, floorId, status);
    }

    @PostMapping
    public Result<RoomResponse> create(@Valid @RequestBody RoomCreateRequest request) {
        return buildingService.createRoom(request);
    }

    @PutMapping("/{id}")
    public Result<RoomResponse> update(@PathVariable Long id, @Valid @RequestBody RoomUpdateRequest request) {
        return buildingService.updateRoom(id, request);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        return buildingService.deleteRoom(id);
    }
}
