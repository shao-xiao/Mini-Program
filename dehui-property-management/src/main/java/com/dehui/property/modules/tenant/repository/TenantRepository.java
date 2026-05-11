package com.dehui.property.modules.tenant.repository;

import com.dehui.property.modules.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findFirstByTenantName(String tenantName);
}
