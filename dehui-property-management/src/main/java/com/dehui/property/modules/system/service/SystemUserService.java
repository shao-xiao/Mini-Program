package com.dehui.property.modules.system.service;

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

        String token = UUID.randomUUID().toString();
        tokenStore.put(token, user);

        List<String> roles = userRoleRepository.findByUserId(user.getId())
                .stream()
                .map(UserRole::getRoleId)
                .map(sysRoleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());

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

        return userRoleRepository.findByUserId(user.getId())
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

    public SysUser createUser(SysUser user) {
        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus("ACTIVE");
        }
        return sysUserRepository.save(user);
    }

    public List<SysUser> listUsers() {
        return sysUserRepository.findAll();
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
        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        return userRoleRepository.save(userRole);
    }

    public List<UserRole> listUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId);
    }
}