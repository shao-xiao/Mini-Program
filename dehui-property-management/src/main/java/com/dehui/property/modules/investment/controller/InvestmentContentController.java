package com.dehui.property.modules.investment.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.investment.entity.InvestmentContent;
import com.dehui.property.modules.investment.service.InvestmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/investment/contents")
@RequiredArgsConstructor
public class InvestmentContentController {

    private final InvestmentService investmentService;

    @PostMapping
    public Result<InvestmentContent> create(@RequestBody InvestmentContent content) {
        return Result.success(investmentService.createContent(content));
    }

    @GetMapping
    public Result<List<InvestmentContent>> list() {
        return Result.success(investmentService.listContents());
    }

    @PutMapping("/{id}")
    public Result<InvestmentContent> update(@PathVariable Long id, @RequestBody InvestmentContent content) {
        return Result.success(investmentService.updateContent(id, content));
    }

    @PostMapping("/{id}/publish")
    public Result<InvestmentContent> publish(@PathVariable Long id) {
        return Result.success(investmentService.publishContent(id));
    }
}
