package com.dehui.property.modules.contract.repository;

import com.dehui.property.modules.contract.entity.ContractEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContractEventRepository extends JpaRepository<ContractEvent, Long> {
    List<ContractEvent> findByContractIdOrderByCreatedTimeDesc(Long contractId);
}
