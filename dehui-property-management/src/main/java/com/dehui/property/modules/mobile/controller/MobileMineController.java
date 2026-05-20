package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileMeResponse;
import com.dehui.property.modules.mobile.dto.MobileMineSummaryResponse;
import com.dehui.property.modules.mobile.service.MobileMineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MobileMineController {

    private final MobileMineService mobileMineService;

    @GetMapping("/mobile/me")
    public Result<MobileMeResponse> me(@RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(mobileMineService.me(token));
    }

    @GetMapping("/mobile/mine/summary")
    public Result<MobileMineSummaryResponse> summary(
            @RequestHeader(value = "Authorization", required = false) String token) {
        return Result.success(mobileMineService.summary(token));
    }
}
