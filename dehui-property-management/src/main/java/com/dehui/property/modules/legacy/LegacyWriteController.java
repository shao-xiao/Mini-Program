package com.dehui.property.modules.legacy;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LegacyWriteController {

    @GetMapping({"/assets", "/equipments", "/energy/readings", "/energy/meters", "/energy/stats", "/feerules", "/inspections"})
    public ApiResponse<List<Object>> legacyLists() {
        return ApiResponse.success(List.of());
    }

    @PostMapping({"/assets", "/equipments", "/energy/readings", "/feerules", "/inspections"})
    public ApiResponse<Void> legacyCreates() {
        throw BusinessException.notImplemented("legacy module");
    }

    @PutMapping({"/assets/{id}", "/equipments/{id}", "/energy/readings/{id}"})
    public ApiResponse<Void> legacyUpdates(@PathVariable Long id) {
        throw BusinessException.notImplemented("legacy module");
    }

    @DeleteMapping({"/assets/{id}", "/energy/readings/{id}"})
    public ApiResponse<Void> legacyDeletes(@PathVariable Long id) {
        throw BusinessException.notImplemented("legacy module");
    }

    @PatchMapping({"/equipments/{id}/status", "/energy/readings/{id}/anomaly-status"})
    public ApiResponse<Void> legacyPatches(@PathVariable Long id) {
        throw BusinessException.notImplemented("legacy module");
    }

    @PostMapping({
            "/assets/{id}/transfer",
            "/energy/readings/{id}/generate-bill",
            "/energy/readings/{id}/mark-posted",
            "/feerules/{id}/generate-bill",
            "/inspections/{id}/close"
    })
    public ApiResponse<Void> legacyActions(@PathVariable Long id) {
        throw BusinessException.notImplemented("legacy module");
    }
}
