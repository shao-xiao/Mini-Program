package com.dehui.property.modules.visitor.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.visitor.entity.VisitorRecord;
import com.dehui.property.modules.visitor.service.VisitorRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/visitors")
@RequiredArgsConstructor
public class VisitorRecordController {

    private final VisitorRecordService visitorRecordService;

    @PostMapping
    public Result<VisitorRecord> create(@RequestBody VisitorRecord record) {
        return Result.success(visitorRecordService.create(record));
    }

    @GetMapping
    public Result<List<VisitorRecord>> list() {
        return Result.success(visitorRecordService.list());
    }

    @GetMapping("/{id}")
    public Result<VisitorRecord> get(@PathVariable Long id) {
        return Result.success(visitorRecordService.get(id));
    }

    @GetMapping("/tenants/{tenantId}")
    public Result<List<VisitorRecord>> listByTenant(@PathVariable Long tenantId) {
        return Result.success(visitorRecordService.listByTenant(tenantId));
    }

    @PatchMapping("/{id}/enter")
    public Result<VisitorRecord> enter(@PathVariable Long id) {
        return Result.success(visitorRecordService.enter(id));
    }

    @PatchMapping("/{id}/leave")
    public Result<VisitorRecord> leave(@PathVariable Long id) {
        return Result.success(visitorRecordService.leave(id));
    }

    @PatchMapping("/{id}/cancel")
    public Result<VisitorRecord> cancel(@PathVariable Long id) {
        return Result.success(visitorRecordService.cancel(id));
    }
}