package com.dehui.property.modules.contract.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.contract.dto.ContractCreateRequest;
import com.dehui.property.modules.contract.dto.ContractResponse;
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

    @PostMapping
    public Result<ContractResponse> create(@Valid @RequestBody ContractCreateRequest request) {
        return contractService.create(request);
    }

    @GetMapping
    public Result<List<ContractResponse>> list() {
        return contractService.findAll();
    }

    @GetMapping("/{id}")
    public Result<ContractResponse> detail(@PathVariable Long id) {
        return contractService.findById(id);
    }

    @PostMapping("/{id}/activate")
    public Result<ContractResponse> activate(@PathVariable Long id) {
        return contractService.activate(id);
    }

    @PostMapping("/{id}/terminate")
    public Result<ContractResponse> terminate(@PathVariable Long id) {
        return contractService.terminate(id);
    }
}
