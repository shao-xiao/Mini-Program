package com.dehui.property.modules.system.repository;

import com.dehui.property.modules.system.entity.RoleMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoleMenuRepository extends JpaRepository<RoleMenu, Long> {
    List<RoleMenu> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);

    boolean existsByRoleIdAndMenuId(Long roleId, Long menuId);
}
