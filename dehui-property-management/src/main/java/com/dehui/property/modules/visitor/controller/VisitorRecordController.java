package com.dehui.property.modules.visitor.controller;

import com.dehui.property.common.ExcelExportUtil;
import com.dehui.property.common.Result;
import com.dehui.property.modules.visitor.entity.VisitorRecord;
import com.dehui.property.modules.visitor.service.VisitorRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
    public Result<List<VisitorRecord>> list(
            @RequestParam(required = false) String visitorName,
            @RequestParam(required = false) String visitorPhone,
            @RequestParam(required = false) String carPlateNo,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String visitedPerson,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(visitorRecordService.list(visitorName, visitorPhone, carPlateNo, tenantId,
                visitedPerson, source, status, startTime, endTime));
    }

    @GetMapping("/{id}")
    public Result<VisitorRecord> get(@PathVariable Long id) {
        return Result.success(visitorRecordService.get(id));
    }

    @GetMapping("/tenants/{tenantId}")
    public Result<List<VisitorRecord>> listByTenant(@PathVariable Long tenantId) {
        return Result.success(visitorRecordService.listByTenant(tenantId));
    }

    @PutMapping("/{id}")
    public Result<VisitorRecord> update(@PathVariable Long id, @RequestBody VisitorRecord record) {
        return Result.success(visitorRecordService.update(id, record));
    }

    @PostMapping("/{id}/approve")
    public Result<VisitorRecord> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> request) {
        return Result.success(visitorRecordService.approve(id, request == null ? null : request.get("reviewedBy")));
    }

    @PostMapping("/{id}/reject")
    public Result<VisitorRecord> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> request) {
        return Result.success(visitorRecordService.reject(
                id,
                request == null ? null : request.get("reviewedBy"),
                request == null ? null : request.get("reason")
        ));
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

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        visitorRecordService.delete(id);
        return Result.success();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String visitorName,
            @RequestParam(required = false) String visitorPhone,
            @RequestParam(required = false) String carPlateNo,
            @RequestParam(required = false) Long tenantId,
            @RequestParam(required = false) String visitedPerson,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return ExcelExportUtil.response("访客管理.xlsx", visitorRecordService.export(visitorName, visitorPhone,
                carPlateNo, tenantId, visitedPerson, source, status, startTime, endTime));
    }
}
