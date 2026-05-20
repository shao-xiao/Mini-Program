package com.dehui.property.modules.announcement.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.announcement.entity.Announcement;
import com.dehui.property.modules.announcement.service.AnnouncementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping
    public Result<Announcement> create(@RequestBody Announcement announcement) {
        return Result.success(announcementService.create(announcement));
    }

    @PutMapping("/{id}")
    public Result<Announcement> update(@PathVariable Long id, @RequestBody Announcement announcement) {
        return Result.success(announcementService.update(id, announcement));
    }

    @GetMapping
    public Result<List<Announcement>> list() {
        return Result.success(announcementService.list());
    }

    @GetMapping("/published")
    public Result<List<Announcement>> listPublished() {
        return Result.success(announcementService.listPublished());
    }

    @PostMapping("/{id}/publish")
    public Result<Announcement> publish(@PathVariable Long id) {
        return Result.success(announcementService.publish(id));
    }
}
