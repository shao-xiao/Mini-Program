package com.dehui.property.modules.system.repository;

import com.dehui.property.modules.system.entity.SysMenu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {
    Optional<SysMenu> findByMenuCode(String menuCode);

    List<SysMenu> findByStatusOrderBySortOrderAscIdAsc(String status);
}
