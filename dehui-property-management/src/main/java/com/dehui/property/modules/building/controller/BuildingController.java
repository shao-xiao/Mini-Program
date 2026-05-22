package com.dehui.property.modules.building.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BuildingController {

    @GetMapping("/buildings")
    public ApiResponse<Void> buildings() {
        throw BusinessException.notImplemented("楼宇");
    }

    @GetMapping("/floors")
    public ApiResponse<Void> floors() {
        throw BusinessException.notImplemented("楼层");
    }

    @GetMapping("/rooms")
    public ApiResponse<Void> rooms() {
        throw BusinessException.notImplemented("房间");
    }

    @GetMapping("/mobile/building/summary")
    public ApiResponse<Void> mobileSummary() {
        throw BusinessException.notImplemented("移动端楼宇摘要");
    }
}
