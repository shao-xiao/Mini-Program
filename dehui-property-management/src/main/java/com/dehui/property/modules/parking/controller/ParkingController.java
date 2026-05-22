package com.dehui.property.modules.parking.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ParkingController {

    @GetMapping("/parking/spaces")
    public ApiResponse<Void> spaces() {
        throw BusinessException.notImplemented("车位");
    }

    @GetMapping("/parking/assignments")
    public ApiResponse<Void> assignments() {
        throw BusinessException.notImplemented("车位绑定");
    }

    @GetMapping("/parking/bills")
    public ApiResponse<Void> bills() {
        throw BusinessException.notImplemented("停车账单");
    }
}
