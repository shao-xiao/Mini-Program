package com.dehui.property.modules.system.repository;

import com.dehui.property.modules.system.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByPhone(String phone);

    List<SysUser> findByUserTypeAndStatusAndDeletedAtIsNull(String userType, String status);

    List<SysUser> findByDeletedAtIsNullOrderByCreatedTimeDesc();
}
