package com.dehui.property.modules.visitor.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisitorController {

    @GetMapping("/visitors")
    public ApiResponse<Void> visitors() {
        throw BusinessException.notImplemented("访客");
    }

    @GetMapping("/mobile/visitors")
    public ApiResponse<Void> mobileVisitors() {
        throw BusinessException.notImplemented("移动端访客");
    }
}
