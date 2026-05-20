package com.dehui.property.config;

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
import com.dehui.property.modules.system.service.SystemUserService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class SystemRbacInitializer {
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final SysMenuRepository menuRepository;
    private final SysPermissionRepository permissionRepository;
    private final RoleMenuRepository roleMenuRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final SystemUserService systemUserService;

    @PostConstruct
    @Transactional
    public void init() {
        seedMenus();
        seedPermissions();
        seedRoles();
        seedAdmin();
        bindDefaults();
    }

    private void seedMenus() {
        menu(null, "dashboard", "驾驶舱", "/dashboard", 10);
        Long asset = menu(null, "asset", "资产管理", null, 20);
        menu(asset, "building:list", "楼宇管理", "/buildings", 21);
        menu(asset, "floor:list", "楼层管理", "/floors", 22);
        menu(asset, "room:list", "房间管理", "/rooms", 23);
        menu(asset, "equipment:list", "设备台账", "/equipment", 24);
        menu(asset, "asset:list", "空间资产", "/assets", 25);
        Long lease = menu(null, "lease", "租赁管理", null, 30);
        menu(lease, "investment:contents", "招商内容", "/investment/contents", 31);
        menu(lease, "investment:leads", "招商线索", "/investment/leads", 32);
        menu(lease, "contract:list", "合同台账", "/contracts", 33);
        menu(lease, "tenant:list", "租户管理", "/tenants", 34);
        menu(lease, "lease:list", "租户入驻", "/leases", 35);
        Long ops = menu(null, "operation", "运营管理", null, 40);
        menu(ops, "workorder:list", "工单管理", "/workorders", 41);
        menu(ops, "inspection:list", "巡检管理", "/inspections", 42);
        menu(ops, "visitor:list", "访客管理", "/visitors", 43);
        menu(ops, "announcement:list", "公告管理", "/announcements", 44);
        Long parking = menu(null, "parking", "停车管理", null, 50);
        menu(parking, "parking:spaces", "车位管理", "/parking/spaces", 51);
        menu(parking, "parking:bills", "停车账单", "/parking/bills", 52);
        Long meeting = menu(null, "meeting", "会议经营", null, 60);
        menu(meeting, "meeting:rooms", "会议室管理", "/meetings/rooms", 61);
        menu(meeting, "meeting:bookings", "会议预约", "/meetings/bookings", 62);
        Long energy = menu(null, "energy", "能耗管理", null, 70);
        menu(energy, "energy:records", "抄表管理", "/energy/records", 71);
        menu(energy, "energy:stats", "能耗统计", "/energy/stats", 72);
        Long finance = menu(null, "finance", "财务管理", null, 80);
        menu(finance, "finance:bills", "账单管理", "/bills", 81);
        menu(finance, "finance:dashboard", "财务看板", "/finance/dashboard", 82);
        Long ai = menu(null, "ai", "AI分析", null, 90);
        menu(ai, "ai:daily-report", "运营日报", "/ai/daily-report", 91);
        Long system = menu(null, "system", "系统管理", null, 100);
        menu(system, "system:users", "用户管理", "/system/users", 101);
        menu(system, "system:roles", "角色管理", "/system/roles", 102);
    }

    private void seedPermissions() {
        perm("dashboard:view", "驾驶舱查看", "MENU", "驾驶舱");
        for (String action : List.of("view", "create", "update", "delete")) {
            perm("asset:" + action, "资产管理-" + action, "ACTION", "资产管理");
            perm("lease:" + action, "租赁管理-" + action, "ACTION", "租赁管理");
        }
        for (String code : List.of(
                "system:user:view", "system:user:create", "system:user:update", "system:user:disable",
                "system:user:delete", "system:user:reset-password", "system:user:assign-role",
                "system:role:view", "system:role:create", "system:role:update", "system:role:disable",
                "system:role:delete", "system:role:assign-permission", "system:menu:view", "system:permission:view")) {
            perm(code, code, "ACTION", "系统管理");
        }
        for (String code : List.of(
                "finance:bill:view", "finance:bill:create", "finance:bill:audit", "finance:bill:pay",
                "finance:bill:export", "finance:invoice:upload", "bill:view", "bill:add", "bill:audit", "bill:pay",
                "feerule:view", "feerule:add", "feerule:generate", "parking-bill:view", "parking-bill:add", "parking-bill:pay")) {
            perm(code, code, "ACTION", "财务管理");
        }
        for (String code : List.of("workorder:view", "workorder:create", "workorder:assign", "workorder:complete", "workorder:close")) {
            perm(code, code, "ACTION", "工单管理");
        }
        for (String code : List.of("parking:view", "parking:create", "parking:update", "meeting:view", "meeting:create",
                "energy:view", "energy:create", "tenant:view", "tenant:create", "contract:view", "contract:create",
                "inspection:view", "visitor:view", "announcement:view", "ai:view", "tenant:portal:view")) {
            perm(code, code, "ACTION", "业务模块");
        }
    }

    private void seedRoles() {
        role("SUPER_ADMIN", "超级管理员", "拥有全部菜单和权限");
        role("PROPERTY_MANAGER", "物业经理", "物业管理和运营负责人");
        role("FINANCE", "财务人员", "账单、收费、财务看板");
        role("OPERATION", "运营人员", "运营、招商、租户、公告");
        role("ASSET_MANAGER", "资产管理员", "楼宇、空间资产、设备台账");
        role("ENGINEER", "工程维修师傅", "工单处理人员");
        role("SECURITY", "安保人员", "访客、巡检、安保事件");
        role("CLEANER", "保洁人员", "保洁工单处理人员");
        role("TENANT_ADMIN", "租户管理员", "租户小程序管理员");
        role("TENANT_USER", "租户普通用户", "租户小程序用户");
        role("ADMIN", "系统管理员(兼容)", "旧角色兼容，视为超级管理员");
        role("MANAGER", "运营经理(兼容)", "旧角色兼容");
        role("STAFF", "普通员工(兼容)", "旧角色兼容");
    }

    private void seedAdmin() {
        SysUser admin = userRepository.findByUsername("admin").orElseGet(() -> {
            SysUser user = new SysUser();
            user.setUsername("admin");
            user.setRealName("系统管理员");
            user.setPhone("13800000000");
            return user;
        });
        admin.setUserType("ADMIN");
        admin.setStatus("ENABLED");
        if (admin.getPasswordHash() == null || admin.getPasswordHash().isBlank()) {
            admin.setPasswordHash(systemUserService.hashPassword(admin.getPassword() == null ? "123456" : admin.getPassword()));
            admin.setPassword(null);
        }
        SysUser saved = userRepository.save(admin);
        bindUserRole(saved.getId(), roleRepository.findByRoleCode("SUPER_ADMIN").orElseThrow().getId());
    }

    private void bindDefaults() {
        SysRole superAdmin = roleRepository.findByRoleCode("SUPER_ADMIN").orElseThrow();
        for (SysMenu menu : menuRepository.findAll()) {
            bindRoleMenu(superAdmin.getId(), menu.getId());
        }
        for (SysPermission permission : permissionRepository.findAll()) {
            bindRolePermission(superAdmin.getId(), permission.getId());
        }

        bindRoleByCodes("FINANCE",
                List.of("dashboard", "finance", "finance:bills", "finance:dashboard", "parking", "parking:bills"),
                List.of("dashboard:view", "finance:bill:view", "finance:bill:create", "finance:bill:audit", "finance:bill:pay",
                        "finance:bill:export", "bill:view", "bill:add", "bill:audit", "bill:pay",
                        "feerule:view", "feerule:add", "feerule:generate", "parking-bill:view", "parking-bill:add", "parking-bill:pay"));
        bindRoleByCodes("ENGINEER",
                List.of("dashboard", "operation", "workorder:list"),
                List.of("dashboard:view", "workorder:view", "workorder:complete"));
        bindRoleByCodes("ASSET_MANAGER",
                List.of("dashboard", "asset", "building:list", "floor:list", "room:list", "equipment:list", "asset:list"),
                List.of("dashboard:view", "asset:view", "asset:create", "asset:update", "asset:delete"));
        bindRoleByCodes("PROPERTY_MANAGER",
                menuRepository.findAll().stream().map(SysMenu::getMenuCode).filter(code -> !code.startsWith("system")).toList(),
                permissionRepository.findAll().stream().map(SysPermission::getPermissionCode).filter(code -> !code.startsWith("system:")).toList());
    }

    private Long menu(Long parentId, String code, String name, String path, int sort) {
        SysMenu menu = menuRepository.findByMenuCode(code).orElseGet(SysMenu::new);
        menu.setParentId(parentId);
        menu.setMenuCode(code);
        menu.setMenuName(name);
        menu.setPath(path);
        menu.setSortOrder(sort);
        menu.setVisible(true);
        menu.setStatus("ENABLED");
        return menuRepository.save(menu).getId();
    }

    private void perm(String code, String name, String type, String module) {
        SysPermission permission = permissionRepository.findByPermissionCode(code).orElseGet(SysPermission::new);
        permission.setPermissionCode(code);
        permission.setPermissionName(name);
        permission.setPermissionType(type);
        permission.setModule(module);
        permissionRepository.save(permission);
    }

    private void role(String code, String name, String desc) {
        SysRole role = roleRepository.findByRoleCode(code).orElseGet(SysRole::new);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDescription(desc);
        role.setStatus("ENABLED");
        roleRepository.save(role);
    }

    private void bindRoleByCodes(String roleCode, List<String> menuCodes, List<String> permissionCodes) {
        SysRole role = roleRepository.findByRoleCode(roleCode).orElse(null);
        if (role == null) {
            return;
        }
        for (String menuCode : menuCodes) {
            menuRepository.findByMenuCode(menuCode).ifPresent(menu -> bindRoleMenu(role.getId(), menu.getId()));
        }
        for (String permissionCode : permissionCodes) {
            permissionRepository.findByPermissionCode(permissionCode).ifPresent(permission -> bindRolePermission(role.getId(), permission.getId()));
        }
    }

    private void bindUserRole(Long userId, Long roleId) {
        if (!userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            UserRole item = new UserRole();
            item.setUserId(userId);
            item.setRoleId(roleId);
            userRoleRepository.save(item);
        }
    }

    private void bindRoleMenu(Long roleId, Long menuId) {
        if (!roleMenuRepository.existsByRoleIdAndMenuId(roleId, menuId)) {
            RoleMenu item = new RoleMenu();
            item.setRoleId(roleId);
            item.setMenuId(menuId);
            roleMenuRepository.save(item);
        }
    }

    private void bindRolePermission(Long roleId, Long permissionId) {
        if (!rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
            RolePermission item = new RolePermission();
            item.setRoleId(roleId);
            item.setPermissionId(permissionId);
            rolePermissionRepository.save(item);
        }
    }
}
