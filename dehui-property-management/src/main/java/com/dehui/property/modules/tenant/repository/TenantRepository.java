package com.dehui.property.modules.tenant.repository;

import com.dehui.property.modules.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
}
