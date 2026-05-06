package com.dehui.property.modules.aiassistant.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.aiassistant.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping("/overdue-bills")
    public Result<List<Map<String, Object>>> getOverdueBills() {
        return Result.success(reminderService.getOverdueBills());
    }
}