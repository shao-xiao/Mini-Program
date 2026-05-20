package com.dehui.property.modules.system.service;

import com.dehui.property.modules.system.dto.AuthMeResponse;
import com.dehui.property.modules.system.dto.ChangePasswordRequest;
import com.dehui.property.modules.system.dto.LoginRequest;
import com.dehui.property.modules.system.dto.LoginResponse;
import com.dehui.property.modules.system.dto.MenuResponse;
import com.dehui.property.modules.system.dto.PermissionResponse;
import com.dehui.property.modules.system.dto.ResetPasswordResponse;
import com.dehui.property.modules.system.dto.RoleRequest;
import com.dehui.property.modules.system.dto.RoleResponse;
import com.dehui.property.modules.system.dto.UserRequest;
import com.dehui.property.modules.system.dto.UserResponse;
import com.dehui.property.modules.system.entity.RoleMenu;
import com.dehui.property.modules.system.entity.RolePermission;
import com.dehui.property.modules.system.entity.SysMenu;
import com.dehui.property.modules.system.entity.SysPermission;
import com.dehui.property.modules.system.entity.SysRole;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.entity.UserRole;
import com.dehui.property.modules.system.repository.RoleMenuRepository;
import com.dehui.property.modules.system.repository.RolePermissionRepository;
import com.dehui.property.modules.system.repository.SysMenuRepository;
import com.dehui.property.modules.system.repository.SysPermissionRepository;
import com.dehui.property.modules.system.repository.SysRoleRepository;
import com.dehui.property.modules.system.repository.SysUserRepository;
import com.dehui.property.modules.system.repository.UserRoleRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class SystemUserService {

    private final SysUserRepository sysUserRepository;
    private final SysRoleRepository sysRoleRepository;
    private final UserRoleRepository userRoleRepository;
    private final SysMenuRepository sysMenuRepository;
    private final SysPermissionRepository sysPermissionRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TenantRepository tenantRepository;

    private final ConcurrentHashMap<String, Long> tokenStore = new ConcurrentHashMap<>();

    @Transactional
    public LoginResponse login(LoginRequest request) {
        SysUser user = sysUserRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        if (user.getDeletedAt() != null || isDisabled(user.getStatus())) {
            throw new RuntimeException("用户已被禁用，禁止登录");
        }

        if (!matchesPassword(user, request.getPassword())) {
            throw new RuntimeException("密码错误");
        }

        if (isBlank(user.getPasswordHash())) {
            user.setPasswordHash(hashPassword(request.getPassword()));
            user.setPassword(null);
        }
        user.setLastLoginAt(LocalDateTime.now());
        SysUser saved = sysUserRepository.save(user);

        String token = UUID.randomUUID().toString();
        tokenStore.put(token, saved.getId());

        List<String> roles = getRoleCodesByUserId(saved.getId());
        List<String> permissions = getPermissionCodesByUserId(saved.getId());
        List<MenuResponse> menus = getMenuTreeByUserId(saved.getId());

        return new LoginResponse(token, saved.getId(), saved.getUsername(), roles, permissions, menus);
    }

    public SysUser getByToken(String token) {
        Long userId = tokenStore.get(cleanToken(token));
        if (userId == null) {
            return null;
        }
        return sysUserRepository.findById(userId)
                .filter(user -> user.getDeletedAt() == null)
                .filter(user -> !isDisabled(user.getStatus()))
                .orElse(null);
    }

    public AuthMeResponse me(String token) {
        SysUser user = getByToken(token);
        if (user == null) {
            return null;
        }
        return new AuthMeResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getPhone(),
                user.getUserType(),
                user.getTenantId(),
                normalizeStatus(user.getStatus()),
                getRoleCodesByUserId(user.getId()),
                getPermissionCodesByUserId(user.getId()),
                getMenuTreeByUserId(user.getId())
        );
    }

    public List<String> getRoleCodesByToken(String token) {
        SysUser user = getByToken(token);
        if (user == null) {
            return List.of();
        }
        return getRoleCodesByUserId(user.getId());
    }

    public boolean hasRole(String token, String roleCode) {
        return getRoleCodesByToken(token).contains(roleCode);
    }

    public boolean hasAnyRole(String token, List<String> roleCodes) {
        List<String> userRoles = getRoleCodesByToken(token);
        return roleCodes.stream().anyMatch(userRoles::contains);
    }

    public boolean hasPermission(String token, String permission) {
        SysUser user = getByToken(token);
        if (user == null) {
            return false;
        }
        if (isSuperAdmin(user.getId())) {
            return true;
        }
        return getPermissionCodesByUserId(user.getId()).contains(permission);
    }

    public boolean hasAnyPermission(String token, List<String> permissions) {
        SysUser user = getByToken(token);
        if (user == null) {
            return false;
        }
        if (isSuperAdmin(user.getId())) {
            return true;
        }
        List<String> owned = getPermissionCodesByUserId(user.getId());
        return permissions.stream().anyMatch(owned::contains);
    }

    @Transactional
    public void changePassword(String token, ChangePasswordRequest request) {
        SysUser user = getByToken(token);
        if (user == null) {
            throw new RuntimeException("未登录或登录已过期");
        }
        if (isBlank(request.getOldPassword())) {
            throw new RuntimeException("原密码不能为空");
        }
        if (isBlank(request.getNewPassword())) {
            throw new RuntimeException("新密码不能为空");
        }
        if (request.getNewPassword().length() < 6) {
            throw new RuntimeException("新密码长度不能少于6位");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的新密码不一致");
        }
        if (!matchesPassword(user, request.getOldPassword())) {
            throw new RuntimeException("原密码错误");
        }
        user.setPasswordHash(hashPassword(request.getNewPassword()));
        user.setPassword(null);
        sysUserRepository.save(user);
    }

    public Map<String, Object> listUsers(String keyword, String username, String realName, String phone,
                                         String userType, String status, int page, int size) {
        List<UserResponse> all = sysUserRepository.findByDeletedAtIsNullOrderByCreatedTimeDesc()
                .stream()
                .filter(user -> contains(user.getUsername(), firstNonBlank(username, keyword)))
                .filter(user -> contains(user.getRealName(), realName))
                .filter(user -> contains(user.getPhone(), phone))
                .filter(user -> isBlank(userType) || userType.equals(user.getUserType()))
                .filter(user -> isBlank(status) || normalizeStatus(status).equals(normalizeStatus(user.getStatus())))
                .map(this::toUserResponse)
                .toList();
        return page(all, page, size);
    }

    public UserResponse getUser(Long id) {
        SysUser user = sysUserRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        return toUserResponse(user);
    }

    @Transactional
    public UserResponse createUser(UserRequest request) {
        validateUserRequest(request, null, true);
        SysUser user = new SysUser();
        applyUser(user, request);
        user.setPasswordHash(hashPassword(isBlank(request.getPassword()) ? "123456" : request.getPassword()));
        user.setPassword(null);
        SysUser saved = sysUserRepository.save(user);
        replaceUserRoles(saved.getId(), request.getRoleIds());
        return toUserResponse(saved);
    }

    @Transactional
    public UserResponse updateUser(Long id, UserRequest request) {
        SysUser user = sysUserRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        validateUserRequest(request, id, false);
        applyUser(user, request);
        SysUser saved = sysUserRepository.save(user);
        if (request.getRoleIds() != null) {
            replaceUserRoles(saved.getId(), request.getRoleIds());
        }
        return toUserResponse(saved);
    }

    @Transactional
    public UserResponse updateUserStatus(Long id, String status) {
        SysUser user = sysUserRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        String normalized = normalizeStatus(status);
        if (!List.of("ENABLED", "DISABLED").contains(normalized)) {
            throw new RuntimeException("非法状态");
        }
        if ("admin".equals(user.getUsername()) && "DISABLED".equals(normalized)) {
            throw new RuntimeException("超级管理员 admin 不允许禁用");
        }
        user.setStatus(normalized);
        return toUserResponse(sysUserRepository.save(user));
    }

    @Transactional
    public ResetPasswordResponse resetPassword(Long id) {
        SysUser user = sysUserRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        String password = String.valueOf(100000 + new java.security.SecureRandom().nextInt(900000));
        user.setPasswordHash(hashPassword(password));
        user.setPassword(null);
        sysUserRepository.save(user);
        return new ResetPasswordResponse(password);
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUser user = sysUserRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        if ("admin".equals(user.getUsername())) {
            throw new RuntimeException("超级管理员用户不允许删除");
        }
        user.setDeletedAt(LocalDateTime.now());
        user.setStatus("DISABLED");
        sysUserRepository.save(user);
        tokenStore.entrySet().removeIf(entry -> id.equals(entry.getValue()));
    }

    @Transactional
    public List<UserRole> assignRoles(Long userId, List<Long> roleIds) {
        if (!sysUserRepository.existsById(userId)) {
            throw new RuntimeException("用户不存在");
        }
        replaceUserRoles(userId, roleIds);
        return userRoleRepository.findByUserId(userId);
    }

    public Map<String, Object> listRoles(String keyword, String roleCode, String roleName, String status, int page, int size) {
        List<RoleResponse> all = sysRoleRepository.findByDeletedAtIsNullOrderByCreatedTimeDesc()
                .stream()
                .filter(role -> contains(role.getRoleCode(), firstNonBlank(roleCode, keyword)))
                .filter(role -> contains(role.getRoleName(), roleName))
                .filter(role -> isBlank(status) || normalizeStatus(status).equals(normalizeStatus(role.getStatus())))
                .map(this::toRoleResponse)
                .toList();
        return page(all, page, size);
    }

    public RoleResponse getRole(Long id) {
        return sysRoleRepository.findById(id)
                .filter(role -> role.getDeletedAt() == null)
                .map(this::toRoleResponse)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        if (isBlank(request.getRoleCode())) {
            throw new RuntimeException("角色编码不能为空");
        }
        if (sysRoleRepository.findByRoleCode(request.getRoleCode()).isPresent()) {
            throw new RuntimeException("角色编码已存在");
        }
        SysRole role = new SysRole();
        applyRole(role, request);
        return toRoleResponse(sysRoleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        SysRole role = sysRoleRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
        if (!Objects.equals(role.getRoleCode(), request.getRoleCode())
                && sysRoleRepository.findByRoleCode(request.getRoleCode()).isPresent()) {
            throw new RuntimeException("角色编码已存在");
        }
        applyRole(role, request);
        return toRoleResponse(sysRoleRepository.save(role));
    }

    @Transactional
    public RoleResponse updateRoleStatus(Long id, String status) {
        SysRole role = sysRoleRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
        role.setStatus(normalizeStatus(status));
        return toRoleResponse(sysRoleRepository.save(role));
    }

    @Transactional
    public void deleteRole(Long id) {
        SysRole role = sysRoleRepository.findById(id)
                .filter(item -> item.getDeletedAt() == null)
                .orElseThrow(() -> new RuntimeException("角色不存在"));
        if ("SUPER_ADMIN".equals(role.getRoleCode())) {
            throw new RuntimeException("超级管理员角色不允许删除");
        }
        role.setDeletedAt(LocalDateTime.now());
        role.setStatus("DISABLED");
        sysRoleRepository.save(role);
        userRoleRepository.deleteByRoleId(id);
        roleMenuRepository.deleteByRoleId(id);
        rolePermissionRepository.deleteByRoleId(id);
    }

    public List<PermissionResponse> listPermissions() {
        return sysPermissionRepository.findByOrderByModuleAscPermissionCodeAsc()
                .stream()
                .map(this::toPermissionResponse)
                .toList();
    }

    public List<MenuResponse> listMenus() {
        return buildMenuTree(sysMenuRepository.findByStatusOrderBySortOrderAscIdAsc("ENABLED"));
    }

    public List<Long> listRolePermissionIds(Long roleId) {
        return rolePermissionRepository.findByRoleId(roleId)
                .stream()
                .map(RolePermission::getPermissionId)
                .toList();
    }

    public List<Long> listRoleMenuIds(Long roleId) {
        return roleMenuRepository.findByRoleId(roleId)
                .stream()
                .map(RoleMenu::getMenuId)
                .toList();
    }

    @Transactional
    public List<Long> assignRolePermissions(Long roleId, List<Long> permissionIds) {
        if (!sysRoleRepository.existsById(roleId)) {
            throw new RuntimeException("角色不存在");
        }
        rolePermissionRepository.deleteByRoleId(roleId);
        for (Long permissionId : nullToEmpty(permissionIds)) {
            if (sysPermissionRepository.existsById(permissionId)) {
                RolePermission item = new RolePermission();
                item.setRoleId(roleId);
                item.setPermissionId(permissionId);
                rolePermissionRepository.save(item);
            }
        }
        return listRolePermissionIds(roleId);
    }

    @Transactional
    public List<Long> assignRoleMenus(Long roleId, List<Long> menuIds) {
        if (!sysRoleRepository.existsById(roleId)) {
            throw new RuntimeException("角色不存在");
        }
        roleMenuRepository.deleteByRoleId(roleId);
        for (Long menuId : nullToEmpty(menuIds)) {
            if (sysMenuRepository.existsById(menuId)) {
                RoleMenu item = new RoleMenu();
                item.setRoleId(roleId);
                item.setMenuId(menuId);
                roleMenuRepository.save(item);
            }
        }
        return listRoleMenuIds(roleId);
    }

    public List<UserRole> listUserRoles(Long userId) {
        return userRoleRepository.findByUserId(userId);
    }

    public List<String> getPermissionCodesByUserId(Long userId) {
        if (isSuperAdmin(userId)) {
            return sysPermissionRepository.findAll()
                    .stream()
                    .map(SysPermission::getPermissionCode)
                    .sorted()
                    .toList();
        }
        List<Long> roleIds = activeRoleIdsByUserId(userId);
        LinkedHashSet<String> codes = new LinkedHashSet<>();
        for (Long roleId : roleIds) {
            rolePermissionRepository.findByRoleId(roleId)
                    .stream()
                    .map(RolePermission::getPermissionId)
                    .map(sysPermissionRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .map(SysPermission::getPermissionCode)
                    .forEach(codes::add);
        }
        return new ArrayList<>(codes);
    }

    public List<String> getRoleCodesByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId)
                .stream()
                .map(UserRole::getRoleId)
                .map(sysRoleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(role -> role.getDeletedAt() == null)
                .filter(role -> !isDisabled(role.getStatus()))
                .map(SysRole::getRoleCode)
                .toList();
    }

    public List<MenuResponse> getMenuTreeByUserId(Long userId) {
        if (isSuperAdmin(userId)) {
            return listMenus();
        }
        LinkedHashSet<Long> menuIds = new LinkedHashSet<>();
        for (Long roleId : activeRoleIdsByUserId(userId)) {
            roleMenuRepository.findByRoleId(roleId)
                    .stream()
                    .map(RoleMenu::getMenuId)
                    .forEach(menuIds::add);
        }
        List<SysMenu> menus = menuIds.stream()
                .map(sysMenuRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(menu -> "ENABLED".equals(normalizeStatus(menu.getStatus())))
                .filter(menu -> Boolean.TRUE.equals(menu.getVisible()))
                .sorted(Comparator.comparing(SysMenu::getSortOrder, Comparator.nullsLast(Integer::compareTo)).thenComparing(SysMenu::getId))
                .toList();
        return buildMenuTree(menus);
    }

    private void validateUserRequest(UserRequest request, Long currentId, boolean create) {
        if (isBlank(request.getUsername())) {
            throw new RuntimeException("用户名不能为空");
        }
        if (create && isBlank(request.getPassword())) {
            throw new RuntimeException("密码不能为空");
        }
        sysUserRepository.findByUsername(request.getUsername())
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new RuntimeException("用户名已存在");
                });
        if (!isBlank(request.getPhone())) {
            sysUserRepository.findByPhone(request.getPhone())
                    .filter(existing -> !existing.getId().equals(currentId))
                    .ifPresent(existing -> {
                        throw new RuntimeException("手机号已存在");
                    });
        }
        if ("TENANT".equals(request.getUserType()) && request.getTenantId() == null) {
            throw new RuntimeException("租户账号必须关联租户");
        }
    }

    private void applyUser(SysUser user, UserRequest request) {
        user.setUsername(request.getUsername().trim());
        user.setRealName(trim(request.getRealName()));
        user.setPhone(trim(request.getPhone()));
        user.setEmail(trim(request.getEmail()));
        user.setUserType(isBlank(request.getUserType()) ? "STAFF" : request.getUserType());
        user.setTenantId(request.getTenantId());
        user.setDepartment(trim(request.getDepartment()));
        user.setStatus(isBlank(request.getStatus()) ? "ENABLED" : normalizeStatus(request.getStatus()));
    }

    private void applyRole(SysRole role, RoleRequest request) {
        role.setRoleCode(request.getRoleCode().trim().toUpperCase(Locale.ROOT));
        role.setRoleName(isBlank(request.getRoleName()) ? role.getRoleCode() : request.getRoleName().trim());
        role.setDescription(trim(request.getDescription()));
        role.setStatus(isBlank(request.getStatus()) ? "ENABLED" : normalizeStatus(request.getStatus()));
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        for (Long roleId : nullToEmpty(roleIds)) {
            if (sysRoleRepository.existsById(roleId) && !userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
                UserRole item = new UserRole();
                item.setUserId(userId);
                item.setRoleId(roleId);
                userRoleRepository.save(item);
            }
        }
    }

    private List<Long> activeRoleIdsByUserId(Long userId) {
        return userRoleRepository.findByUserId(userId)
                .stream()
                .map(UserRole::getRoleId)
                .map(sysRoleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(role -> role.getDeletedAt() == null)
                .filter(role -> !isDisabled(role.getStatus()))
                .map(SysRole::getId)
                .toList();
    }

    private boolean isSuperAdmin(Long userId) {
        return getRoleCodesByUserId(userId).stream().anyMatch(role -> "SUPER_ADMIN".equals(role) || "ADMIN".equals(role));
    }

    private UserResponse toUserResponse(SysUser user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRealName(user.getRealName());
        response.setPhone(user.getPhone());
        response.setEmail(user.getEmail());
        response.setUserType(user.getUserType());
        response.setUserTypeText(userTypeText(user.getUserType()));
        response.setTenantId(user.getTenantId());
        if (user.getTenantId() != null) {
            tenantRepository.findById(user.getTenantId()).ifPresent(tenant -> response.setTenantName(tenant.getTenantName()));
        }
        response.setDepartment(user.getDepartment());
        response.setStatus(normalizeStatus(user.getStatus()));
        response.setStatusText("DISABLED".equals(response.getStatus()) ? "禁用" : "启用");
        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        response.setRoleIds(userRoles.stream().map(UserRole::getRoleId).toList());
        List<SysRole> roles = userRoles.stream()
                .map(UserRole::getRoleId)
                .map(sysRoleRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
        response.setRoleCodes(roles.stream().map(SysRole::getRoleCode).toList());
        response.setRoleNames(roles.stream().map(SysRole::getRoleName).toList());
        response.setLastLoginAt(user.getLastLoginAt());
        response.setCreatedTime(user.getCreatedTime());
        response.setUpdatedTime(user.getUpdatedTime());
        return response;
    }

    private RoleResponse toRoleResponse(SysRole role) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setRoleCode(role.getRoleCode());
        response.setRoleName(role.getRoleName());
        response.setDescription(role.getDescription());
        response.setStatus(normalizeStatus(role.getStatus()));
        response.setStatusText("DISABLED".equals(response.getStatus()) ? "禁用" : "启用");
        response.setCreatedTime(role.getCreatedTime());
        response.setUpdatedTime(role.getUpdatedTime());
        return response;
    }

    private PermissionResponse toPermissionResponse(SysPermission permission) {
        PermissionResponse response = new PermissionResponse();
        response.setId(permission.getId());
        response.setPermissionCode(permission.getPermissionCode());
        response.setPermissionName(permission.getPermissionName());
        response.setPermissionType(permission.getPermissionType());
        response.setModule(permission.getModule());
        response.setDescription(permission.getDescription());
        return response;
    }

    private MenuResponse toMenuResponse(SysMenu menu) {
        MenuResponse response = new MenuResponse();
        response.setId(menu.getId());
        response.setParentId(menu.getParentId());
        response.setMenuName(menu.getMenuName());
        response.setTitle(menu.getMenuName());
        response.setMenuCode(menu.getMenuCode());
        response.setPath(menu.getPath());
        response.setComponent(menu.getComponent());
        response.setIcon(menu.getIcon());
        response.setSortOrder(menu.getSortOrder());
        response.setVisible(menu.getVisible());
        response.setStatus(normalizeStatus(menu.getStatus()));
        return response;
    }

    private List<MenuResponse> buildMenuTree(List<SysMenu> menus) {
        Map<Long, MenuResponse> map = new LinkedHashMap<>();
        for (SysMenu menu : menus) {
            if (!Boolean.FALSE.equals(menu.getVisible())) {
                map.put(menu.getId(), toMenuResponse(menu));
            }
        }
        List<MenuResponse> roots = new ArrayList<>();
        for (MenuResponse menu : map.values()) {
            if (menu.getParentId() == null || !map.containsKey(menu.getParentId())) {
                roots.add(menu);
            } else {
                map.get(menu.getParentId()).getChildren().add(menu);
            }
        }
        return roots;
    }

    private <T> Map<String, Object> page(List<T> all, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        int from = Math.min(safePage * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("records", all.subList(from, to));
        data.put("total", all.size());
        data.put("page", safePage);
        data.put("size", safeSize);
        return data;
    }

    private boolean matchesPassword(SysUser user, String raw) {
        if (raw == null) {
            return false;
        }
        if (!isBlank(user.getPasswordHash())) {
            return user.getPasswordHash().equals(hashPassword(raw));
        }
        return raw.equals(user.getPassword());
    }

    public String hashPassword(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256不可用", e);
        }
    }

    private String cleanToken(String token) {
        if (token == null) {
            return null;
        }
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    private boolean isDisabled(String status) {
        return "DISABLED".equals(normalizeStatus(status));
    }

    private String normalizeStatus(String status) {
        if ("ACTIVE".equals(status)) {
            return "ENABLED";
        }
        if (isBlank(status)) {
            return "ENABLED";
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private String userTypeText(String userType) {
        return switch (userType == null ? "" : userType) {
            case "ADMIN" -> "系统管理员";
            case "MANAGER" -> "运营/管理人员";
            case "FINANCE" -> "财务人员";
            case "ENGINEER" -> "工程维修人员";
            case "SECURITY" -> "安保人员";
            case "CLEANER" -> "保洁人员";
            case "TENANT" -> "租户用户";
            case "STAFF" -> "普通员工";
            default -> isBlank(userType) ? "普通员工" : userType;
        };
    }

    private String firstNonBlank(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private boolean contains(String source, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        return source != null && source.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private List<Long> nullToEmpty(List<Long> values) {
        return values == null ? List.of() : values;
    }
}
