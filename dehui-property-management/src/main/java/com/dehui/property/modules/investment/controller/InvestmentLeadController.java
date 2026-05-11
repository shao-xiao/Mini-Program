package com.dehui.property.modules.investment.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.investment.dto.InvestmentLeadAdminResponse;
import com.dehui.property.modules.investment.dto.InvestmentLeadStatusRequest;
import com.dehui.property.modules.investment.service.InvestmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/investment/leads")
@RequiredArgsConstructor
public class InvestmentLeadController {

    private final InvestmentService investmentService;

    @GetMapping
    public Result<List<InvestmentLeadAdminResponse>> list() {
        return Result.success(investmentService.listLeads());
    }

    @PatchMapping("/{id}/status")
    public Result<InvestmentLeadAdminResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody InvestmentLeadStatusRequest request) {
        return Result.success(investmentService.updateLeadStatus(id, request.getStatus()));
    }
}
