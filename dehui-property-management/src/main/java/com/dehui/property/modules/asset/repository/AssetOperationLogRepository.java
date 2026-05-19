package com.dehui.property.modules.asset.repository;

import com.dehui.property.modules.asset.entity.AssetOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetOperationLogRepository extends JpaRepository<AssetOperationLog, Long> {
    List<AssetOperationLog> findByAssetIdOrderByOperationTimeDesc(Long assetId);
}
