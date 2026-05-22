package com.dehui.property.modules.notice.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NoticeController {

    @GetMapping("/notices")
    public ApiResponse<Void> notices() {
        throw BusinessException.notImplemented("公告");
    }

    @GetMapping("/mobile/announcements")
    public ApiResponse<Void> mobileAnnouncements() {
        throw BusinessException.notImplemented("移动端公告");
    }
}
