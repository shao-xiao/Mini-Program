package com.dehui.property.modules.asset.repository;

import com.dehui.property.modules.asset.entity.Asset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetRepository extends JpaRepository<Asset, Long> {
    boolean existsByAssetCodeAndDeletedAtIsNull(String assetCode);

    Optional<Asset> findByAssetCodeAndDeletedAtIsNull(String assetCode);

    Optional<Asset> findByIdAndDeletedAtIsNull(Long id);

    List<Asset> findByDeletedAtIsNullOrderByCreatedTimeDesc();

    long countByRoomIdAndDeletedAtIsNull(Long roomId);

    long countByDeletedAtIsNull();

    long countByStatusAndDeletedAtIsNull(String status);

    List<Asset> findByWarrantyEndDateBetweenAndDeletedAtIsNull(LocalDate start, LocalDate end);

    List<Asset> findByNextMaintenanceDateLessThanEqualAndDeletedAtIsNull(LocalDate date);
}
