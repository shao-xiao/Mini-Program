package com.dehui.property.modules.mobile.repository;

import com.dehui.property.modules.mobile.entity.StaffCheckin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StaffCheckinRepository extends JpaRepository<StaffCheckin, Long> {
    List<StaffCheckin> findBySysUserIdOrderByCheckinTimeDesc(Long sysUserId);
}
