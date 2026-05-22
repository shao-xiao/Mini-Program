package com.dehui.property.modules.checkin.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckinController {

    @GetMapping("/checkins")
    public ApiResponse<Void> checkins() {
        throw BusinessException.notImplemented("员工签到");
    }

    @GetMapping("/mobile/checkins")
    public ApiResponse<Void> mobileCheckins() {
        throw BusinessException.notImplemented("移动端员工签到");
    }
}
