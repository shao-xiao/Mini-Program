package com.dehui.property.modules.contract.repository;

import com.dehui.property.modules.contract.entity.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByLeaseId(Long leaseId);

    boolean existsByLeaseId(Long leaseId);

    boolean existsByContractNumber(String contractNumber);

    List<Contract> findByStatus(String status);

    List<Contract> findByStatusAndLeaseIdIsNull(String status);

    @Query(value = "SELECT COUNT(*) FROM contract WHERE status = :status", nativeQuery = true)
    Long countByStatus(@Param("status") String status);
}
