package com.dehui.property.modules.bill.repository;

import com.dehui.property.modules.bill.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BillRepository extends JpaRepository<Bill, Long> {

    List<Bill> findByTenantIdOrderByCreatedTimeDesc(Long tenantId);

    List<Bill> findByContractId(Long contractId);

    List<Bill> findByStatusOrderByDueDateAsc(String status);

    List<Bill> findByContractIdOrderByPeriodStartDesc(Long contractId);
    
    List<Bill> findByStatusAndDueDateBefore(String status, LocalDate date);

    boolean existsByBillNumber(String billNumber);

    Optional<Bill> findByBillNumber(String billNumber);

    boolean existsByContractIdAndBillTypeAndPeriodStart(Long contractId, String billType, LocalDate periodStart);

    @Query(value = "SELECT COUNT(*) FROM bill WHERE status = :status", nativeQuery = true)
    Long countByStatus(@Param("status") String status);

    @Query(value = "SELECT COALESCE(SUM(paid_amount), 0) FROM bill WHERE status = 'PAID' AND DATE(updated_time) = :date", nativeQuery = true)
    BigDecimal sumPaidAmountByDate(@Param("date") LocalDate date);
}
