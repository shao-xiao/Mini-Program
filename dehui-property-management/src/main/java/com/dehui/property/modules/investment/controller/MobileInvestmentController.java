package com.dehui.property.modules.investment.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.investment.dto.InvestmentLeadRequest;
import com.dehui.property.modules.investment.dto.InvestmentLeadResponse;
import com.dehui.property.modules.investment.dto.InvestmentOverviewResponse;
import com.dehui.property.modules.investment.dto.InvestmentRoomResponse;
import com.dehui.property.modules.investment.service.InvestmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/mobile/investment")
@RequiredArgsConstructor
public class MobileInvestmentController {

    private final InvestmentService investmentService;

    @GetMapping("/overview")
    public Result<InvestmentOverviewResponse> overview() {
        return Result.success(investmentService.overview());
    }

    @GetMapping("/rooms")
    public Result<List<InvestmentRoomResponse>> rooms() {
        return Result.success(investmentService.availableRooms());
    }

    @PostMapping("/leads")
    public Result<InvestmentLeadResponse> createLead(@Valid @RequestBody InvestmentLeadRequest request) {
        return Result.success(investmentService.createLead(request));
    }
}
