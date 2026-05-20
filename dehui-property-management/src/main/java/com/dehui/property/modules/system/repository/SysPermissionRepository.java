package com.dehui.property.modules.system.repository;

import com.dehui.property.modules.system.entity.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {
    Optional<SysPermission> findByPermissionCode(String permissionCode);

    List<SysPermission> findByOrderByModuleAscPermissionCodeAsc();
}
