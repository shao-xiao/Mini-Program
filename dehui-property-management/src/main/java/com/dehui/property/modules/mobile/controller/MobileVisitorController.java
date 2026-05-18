package com.dehui.property.modules.mobile.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.dto.MobileVisitorHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileVisitorRequest;
import com.dehui.property.modules.mobile.dto.MobileVisitorResponse;
import com.dehui.property.modules.mobile.service.MobileVisitorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mobile/visitors")
@RequiredArgsConstructor
public class MobileVisitorController {

    private final MobileVisitorService mobileVisitorService;

    @GetMapping
    public Result<MobileVisitorHomeResponse> home(@RequestHeader("Authorization") String token) {
        return mobileVisitorService.home(normalizeToken(token));
    }

    @PostMapping
    public Result<MobileVisitorResponse> create(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MobileVisitorRequest request) {
        return mobileVisitorService.create(normalizeToken(token), request);
    }

    @RequestMapping(value = "/{id}/cancel", method = {RequestMethod.PATCH, RequestMethod.POST})
    public Result<MobileVisitorResponse> cancel(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        return mobileVisitorService.cancel(normalizeToken(token), id);
    }

    private String normalizeToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
}
