package com.dehui.property.modules.contract.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.contract.dto.BillGenerationResult;
import com.dehui.property.modules.contract.dto.ContractActionRequest;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractEventResponse;
import com.dehui.property.modules.contract.dto.ContractResponse;
import com.dehui.property.modules.contract.service.ContractBillGenerationService;
import com.dehui.property.modules.contract.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contracts")
@RequiredArgsConstructor
public class ContractController {
    private final ContractService contractService;
    private final ContractBillGenerationService contractBillGenerationService;

    @PostMapping
    public Result<ContractResponse> create(@Valid @RequestBody ContractCreateRequest request) {
        return contractService.create(request);
    }

    @GetMapping
    public Result<List<ContractResponse>> list() {
        return contractService.findAll();
    }

    @GetMapping("/pending-checkin")
    public Result<List<ContractResponse>> pendingCheckin() {
        return contractService.findActivePendingCheckin();
    }

    @PostMapping("/generate-bills")
    public Result<BillGenerationResult> generateBills() {
        return Result.success(contractBillGenerationService.generateDueBills());
    }

    @GetMapping("/{id}")
    public Result<ContractResponse> detail(@PathVariable Long id) {
        return contractService.findById(id);
    }

    @PostMapping("/{id}/activate")
    public Result<ContractResponse> activate(@PathVariable Long id) {
        return contractService.activate(id);
    }

    @PostMapping("/{id}/check-in")
    public Result<ContractResponse> checkIn(@PathVariable Long id,
                                            @RequestBody(required = false) ContractActionRequest request) {
        return contractService.checkIn(id, request);
    }

    @PostMapping("/{id}/terminate")
    public Result<ContractResponse> terminate(@PathVariable Long id,
                                              @RequestBody(required = false) ContractActionRequest request) {
        return contractService.terminate(id, request);
    }

    @PostMapping("/{id}/cancel")
    public Result<ContractResponse> cancel(@PathVariable Long id,
                                           @RequestBody(required = false) ContractActionRequest request) {
        return contractService.cancel(id, request);
    }

    @GetMapping("/{id}/events")
    public Result<List<ContractEventResponse>> events(@PathVariable Long id) {
        return contractService.events(id);
    }
}
