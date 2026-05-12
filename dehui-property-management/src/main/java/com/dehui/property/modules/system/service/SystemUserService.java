package com.dehui.property.modules.system.service;

import com.dehui.property.modules.system.dto.ChangePasswordRequest;
import com.dehui.property.modules.system.dto.LoginRequest;
import com.dehui.property.modules.system.dto.LoginResponse;
import com.dehui.property.modules.system.entity.SysRole;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.entity.UserRole;
import com.dehui.property.modules.system.repository.SysRoleRepository;
import com.dehui.property.modules.system.repository.SysUserRepository;
import com.dehui.property.modules.system.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemUserService {

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final UserRoleRepository userRoleRepository;

    private final ConcurrentHashMap<String, SysUser> tokenStore = new ConcurrentHashMap<>();

    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!user.getPassword().equals(request.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("用户已被禁用，禁止登录");
        }

        String token = UUID.randomUUID().toString();
        tokenStore.put(token, user);

        List<String> roles = getRoleCodesByUserId(user.getId());

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                roles
        );
    }

    public SysUser getByToken(String token) {
        return tokenStore.get(token);
    }

    public List<String> getRoleCodesByToken(String token) {
        SysUser user = getByToken(token);
        if (user == null) {
            return List.of();
        }

        return getRoleCodesByUserId(user.getId());
    }

    private List<String> getRoleCodesByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId)
                .stream()
                .map(UserRole::getRoleId)
                .map(sysRoleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
    }

    public boolean hasRole(String token, String roleCode) {
        return getRoleCodesByToken(token).contains(roleCode);
    }

    public boolean hasAnyRole(String token, List<String> roleCodes) {
        List<String> userRoles = getRoleCodesByToken(token);
        return roleCodes.stream().anyMatch(userRoles::contains);
    }

    @Transactional
    public void changePassword(String token, ChangePasswordRequest request) {
        SysUser user = getByToken(token);
        if (user == null) {
            throw new RuntimeException("未登录或登录已过期");
        }

        if (request.getOldPassword() == null || request.getOldPassword().isBlank()) {
            throw new RuntimeException("原密码不能为空");
        }

        if (request.getNewPassword() == null || request.getNewPassword().isBlank()) {
            throw new RuntimeException("新密码不能为空");
        }

        if (request.getNewPassword().length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的新密码不一致");
        }

        if (!user.getPassword().equals(request.getOldPassword())) {
            throw new RuntimeException("原密码错误");
        }

        if (user.getPassword().equals(request.getNewPassword())) {
            throw new RuntimeException("新密码不能与原密码相同");
        }

        user.setPassword(request.getNewPassword());
        SysUser savedUser = sysUserRepository.save(user);
        tokenStore.put(token, savedUser);
    }

    public boolean hasPermission(String token, String permission) {
        List<String> roles = getRoleCodesByToken(token);

        if (roles.contains("ADMIN")) {
            return true;
        }

        return switch (permission) {
            case "bill:view" ->
                    roles.stream().anyMatch(r -> List.of("MANAGER", "FINANCE").contains(r));
            case "bill:add" ->
                    roles.stream().anyMatch(r -> List.of("MANAGER", "FINANCE").contains(r));
            case "bill:pay" ->
                    roles.stream().anyMatch(r -> List.of("FINANCE").contains(r));

            case "feerule:view" ->
                    roles.stream().anyMatch(r -> List.of("MANAGER", "FINANCE").contains(r));
            case "feerule:add" ->
                    roles.stream().anyMatch(r -> List.of("FINANCE").contains(r));
            case "feerule:generate" ->
                    roles.stream().anyMatch(r -> List.of("FINANCE").contains(r));

            case "parking-bill:view" ->
                    roles.stream().anyMatch(r -> List.of("MANAGER", "FINANCE").contains(r));
            case "parking-bill:add" ->
                    roles.stream().anyMatch(r -> List.of("FINANCE").contains(r));
            case "parking-bill:pay" ->
                    roles.stream().anyMatch(r -> List.of("FINANCE").contains(r));

            default -> false;
        };
    }

    public SysUser createUser(SysUser user) {
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new RuntimeException("密码不能为空");
        }

        if (sysUserRepository.findByUsername(user.getUsername()).isPresent()) {
            throw new RuntimeException("用户名已存在，请勿重复创建");
        }

        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus("ACTIVE");
        }

        return sysUserRepository.save(user);
    }

    public List<SysUser> listUsers() {
        return sysUserRepository.findAll();
    }

    public SysUser updateUserStatus(Long id, String status) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (!List.of("ACTIVE", "DISABLED").contains(status)) {
            throw new RuntimeException("非法状态");
        }

        if ("admin".equals(user.getUsername()) && "DISABLED".equals(status)) {
            throw new RuntimeException("超级管理员 admin 不允许禁用");
        }

        user.setStatus(status);
        return sysUserRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUser user = sysUserRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if ("admin".equals(user.getUsername())) {
            throw new RuntimeException("超级管理员用户不允许删除");
        }

        userRoleRepository.deleteByUserId(user.getId());
        tokenStore.entrySet().removeIf(entry -> user.getId().equals(entry.getValue().getId()));
        sysUserRepository.delete(user);
    }

    public SysRole createRole(SysRole role) {
        if (role.getStatus() == null || role.getStatus().isBlank()) {
            role.setStatus("ACTIVE");
        }
        return sysRoleRepository.save(role);
    }

    public List<SysRole> listRoles() {
        return sysRoleRepository.findAll();
    }

    public UserRole assignRole(Long userId, Long roleId) {
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            throw new RuntimeException("该用户已分配此角色，请勿重复分配");
        }

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);

        return userRoleRepository.save(userRole);
    }

    public List<UserRole> listUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId);
    }
}
