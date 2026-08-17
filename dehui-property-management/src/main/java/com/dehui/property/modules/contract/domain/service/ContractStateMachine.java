package com.dehui.property.modules.contract.domain.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.contract.domain.model.ContractStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class ContractStateMachine {

    private static final Map<ContractStatus, Set<ContractStatus>> TRANSITIONS = transitions();

    private ContractStateMachine() {
    }

    public static boolean canTransition(ContractStatus from, ContractStatus to) {
        return from != null && to != null && TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireTransition(ContractStatus from, ContractStatus to) {
        if (!canTransition(from, to)) {
            throw new BusinessException(409, "合同状态不允许从 " + from + " 变更为 " + to);
        }
    }

    private static Map<ContractStatus, Set<ContractStatus>> transitions() {
        Map<ContractStatus, Set<ContractStatus>> result = new EnumMap<>(ContractStatus.class);
        result.put(ContractStatus.DRAFT, EnumSet.of(ContractStatus.PENDING_APPROVAL, ContractStatus.CANCELLED));
        result.put(ContractStatus.PENDING_APPROVAL, EnumSet.of(ContractStatus.ACTIVE, ContractStatus.CANCELLED));
        result.put(ContractStatus.ACTIVE, EnumSet.of(
                ContractStatus.SUSPENDED,
                ContractStatus.TERMINATED,
                ContractStatus.EXPIRED
        ));
        result.put(ContractStatus.SUSPENDED, EnumSet.of(ContractStatus.ACTIVE, ContractStatus.TERMINATED));
        return Map.copyOf(result);
    }
}
