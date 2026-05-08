package com.dehui.property.modules.system.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.system.dto.ChangePasswordRequest;
import com.dehui.property.modules.system.dto.LoginRequest;
import com.dehui.property.modules.system.dto.LoginResponse;
import com.dehui.property.modules.system.entity.SysRole;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.entity.UserRole;
import com.dehui.property.modules.system.service.SystemUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemUserController {

    private final SystemUserService systemUserService;

    @PatchMapping("/me/password")
    public Result<Void> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody ChangePasswordRequest request) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        systemUserService.changePassword(token, request);
        return Result.success();
    }

    @PostMapping("/users")
    public Result<SysUser> createUser(@RequestBody SysUser user) {
        return Result.success(systemUserService.createUser(user));
    }

    @GetMapping("/users")
    public Result<List<SysUser>> listUsers() {
        return Result.success(systemUserService.listUsers());
    }

    @PatchMapping("/users/{id}/status")
    public Result<SysUser> updateUserStatus(
            @PathVariable Long id,
            @RequestParam String status) {
        return Result.success(systemUserService.updateUserStatus(id, status));
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        systemUserService.deleteUser(id);
        return Result.success();
    }

    @PostMapping("/roles")
    public Result<SysRole> createRole(@RequestBody SysRole role) {
        return Result.success(systemUserService.createRole(role));
    }

    @GetMapping("/roles")
    public Result<List<SysRole>> listRoles() {
        return Result.success(systemUserService.listRoles());
    }

    @PostMapping("/users/{userId}/roles/{roleId}")
    public Result<UserRole> assignRole(
            @PathVariable Long userId,
            @PathVariable Long roleId) {
        return Result.success(systemUserService.assignRole(userId, roleId));
    }

    @GetMapping("/users/{userId}/roles")
    public Result<List<UserRole>> listUserRoles(@PathVariable Long userId) {
        return Result.success(systemUserService.listUserRoles(userId));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(systemUserService.login(request));
    }
}
