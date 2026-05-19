package com.dehui.property.modules.mobile.repository;

import com.dehui.property.modules.mobile.entity.TenantContact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TenantContactRepository extends JpaRepository<TenantContact, Long> {
    Optional<TenantContact> findByPhone(String phone);

    List<TenantContact> findByTenantId(Long tenantId);

    List<TenantContact> findByTenantIdOrderByIsPrimaryDescIdAsc(Long tenantId);
}
