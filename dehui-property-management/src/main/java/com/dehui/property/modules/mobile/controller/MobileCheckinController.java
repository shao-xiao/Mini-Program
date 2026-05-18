package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileCheckinHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileCheckinRequest;
import com.dehui.property.modules.mobile.dto.MobileCheckinResponse;
import com.dehui.property.modules.mobile.service.MobileCheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mobile/checkins")
@RequiredArgsConstructor
public class MobileCheckinController {

    private final MobileCheckinService mobileCheckinService;

    @GetMapping
    public Result<MobileCheckinHomeResponse> home(@RequestHeader("Authorization") String token) {
        return mobileCheckinService.home(normalizeToken(token));
    }

    @PostMapping
    public Result<MobileCheckinResponse> create(
            @RequestHeader("Authorization") String token,
            @RequestBody MobileCheckinRequest request) {
        return mobileCheckinService.create(normalizeToken(token), request);
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
