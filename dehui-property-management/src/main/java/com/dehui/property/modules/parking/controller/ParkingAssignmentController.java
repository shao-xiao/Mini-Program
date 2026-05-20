package com.dehui.property.modules.parking.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.parking.dto.ParkingAssignmentResponse;
import com.dehui.property.modules.parking.service.ParkingSpaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/parking/assignments")
@RequiredArgsConstructor
public class ParkingAssignmentController {
    private final ParkingSpaceService parkingSpaceService;

    @GetMapping
    public Result<List<ParkingAssignmentResponse>> list(
            @RequestParam(required = false) Long spaceId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String partyType) {
        return Result.success(parkingSpaceService.listAssignments(spaceId, status, partyType));
    }
}
