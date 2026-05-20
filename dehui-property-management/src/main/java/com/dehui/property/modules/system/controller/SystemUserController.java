package com.dehui.property.modules.system.controller;

import com.dehui.property.common.Result;
import com.dehui.property.modules.system.dto.ChangePasswordRequest;
import com.dehui.property.modules.system.dto.IdListRequest;
import com.dehui.property.modules.system.dto.LoginRequest;
import com.dehui.property.modules.system.dto.LoginResponse;
import com.dehui.property.modules.system.dto.MenuResponse;
import com.dehui.property.modules.system.dto.PermissionResponse;
import com.dehui.property.modules.system.dto.ResetPasswordResponse;
import com.dehui.property.modules.system.dto.RoleRequest;
import com.dehui.property.modules.system.dto.RoleResponse;
import com.dehui.property.modules.system.dto.UserRequest;
import com.dehui.property.modules.system.dto.UserResponse;
import com.dehui.property.modules.system.entity.UserRole;
import com.dehui.property.modules.system.service.SystemUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemUserController {

    private final SystemUserService systemUserService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(systemUserService.login(request));
    }

    @PatchMapping("/me/password")
    public Result<Void> changePassword(
            @RequestHeader("Authorization") String token,
            @RequestBody ChangePasswordRequest request) {
        systemUserService.changePassword(token, request);
        return Result.success();
    }

    @GetMapping("/users")
    public Result<Map<String, Object>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false, name = "user_type") String userTypeSnake,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(systemUserService.listUsers(
                keyword,
                username,
                realName,
                phone,
                userType == null ? userTypeSnake : userType,
                status,
                page,
                size
        ));
    }

    @GetMapping("/users/{id}")
    public Result<UserResponse> getUser(@PathVariable Long id) {
        return Result.success(systemUserService.getUser(id));
    }

    @PostMapping("/users")
    public Result<UserResponse> createUser(@RequestBody UserRequest request) {
        return Result.success(systemUserService.createUser(request));
    }

    @PutMapping("/users/{id}")
    public Result<UserResponse> updateUser(@PathVariable Long id, @RequestBody UserRequest request) {
        return Result.success(systemUserService.updateUser(id, request));
    }

    @PatchMapping("/users/{id}/status")
    public Result<UserResponse> updateUserStatus(@PathVariable Long id, @RequestParam String status) {
        return Result.success(systemUserService.updateUserStatus(id, status));
    }

    @PatchMapping("/users/{id}/reset-password")
    public Result<ResetPasswordResponse> resetPassword(@PathVariable Long id) {
        return Result.success(systemUserService.resetPassword(id));
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        systemUserService.deleteUser(id);
        return Result.success();
    }

    @PostMapping("/users/{id}/roles")
    public Result<List<UserRole>> assignRoles(@PathVariable Long id, @RequestBody IdListRequest request) {
        return Result.success(systemUserService.assignRoles(id, request.getIds()));
    }

    @PostMapping("/users/{userId}/roles/{roleId}")
    public Result<List<UserRole>> assignRole(@PathVariable Long userId, @PathVariable Long roleId) {
        return Result.success(systemUserService.assignRoles(userId, List.of(roleId)));
    }

    @GetMapping("/users/{userId}/roles")
    public Result<List<UserRole>> listUserRoles(@PathVariable Long userId) {
        return Result.success(systemUserService.listUserRoles(userId));
    }

    @GetMapping("/roles")
    public Result<Map<String, Object>> listRoles(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return Result.success(systemUserService.listRoles(keyword, roleCode, roleName, status, page, size));
    }

    @GetMapping("/roles/{id}")
    public Result<RoleResponse> getRole(@PathVariable Long id) {
        return Result.success(systemUserService.getRole(id));
    }

    @PostMapping("/roles")
    public Result<RoleResponse> createRole(@RequestBody RoleRequest request) {
        return Result.success(systemUserService.createRole(request));
    }

    @PutMapping("/roles/{id}")
    public Result<RoleResponse> updateRole(@PathVariable Long id, @RequestBody RoleRequest request) {
        return Result.success(systemUserService.updateRole(id, request));
    }

    @PatchMapping("/roles/{id}/status")
    public Result<RoleResponse> updateRoleStatus(@PathVariable Long id, @RequestParam String status) {
        return Result.success(systemUserService.updateRoleStatus(id, status));
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id) {
        systemUserService.deleteRole(id);
        return Result.success();
    }

    @GetMapping("/roles/{id}/permissions")
    public Result<List<Long>> rolePermissions(@PathVariable Long id) {
        return Result.success(systemUserService.listRolePermissionIds(id));
    }

    @PostMapping("/roles/{id}/permissions")
    public Result<List<Long>> assignRolePermissions(@PathVariable Long id, @RequestBody IdListRequest request) {
        return Result.success(systemUserService.assignRolePermissions(id, request.getIds()));
    }

    @GetMapping("/roles/{id}/menus")
    public Result<List<Long>> roleMenus(@PathVariable Long id) {
        return Result.success(systemUserService.listRoleMenuIds(id));
    }

    @PostMapping("/roles/{id}/menus")
    public Result<List<Long>> assignRoleMenus(@PathVariable Long id, @RequestBody IdListRequest request) {
        return Result.success(systemUserService.assignRoleMenus(id, request.getIds()));
    }

    @GetMapping("/menus")
    public Result<List<MenuResponse>> menus() {
        return Result.success(systemUserService.listMenus());
    }

    @GetMapping("/permissions")
    public Result<List<PermissionResponse>> permissions() {
        return Result.success(systemUserService.listPermissions());
    }
}
