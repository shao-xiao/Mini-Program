package com.dehui.property.modules.workorder.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WorkOrderController {

    @GetMapping("/workorders")
    public ApiResponse<Void> workorders() {
        throw BusinessException.notImplemented("工单");
    }

    @GetMapping("/mobile/workorders")
    public ApiResponse<Void> mobileWorkorders() {
        throw BusinessException.notImplemented("移动端工单");
    }
}
