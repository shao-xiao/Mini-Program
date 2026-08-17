package com.dehui.property.modules.contract;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ContractStatusMachineTest {

    private static final String STATUS_CLASS =
            "com.dehui.property.modules.contract.domain.model.ContractStatus";
    private static final String MACHINE_CLASS =
            "com.dehui.property.modules.contract.domain.service.ContractStateMachine";

    @Test
    void permitsApprovalActivationSuspensionAndTerminationLifecycle() throws Exception {
        assertTrue(canTransition("DRAFT", "PENDING_APPROVAL"));
        assertTrue(canTransition("PENDING_APPROVAL", "ACTIVE"));
        assertTrue(canTransition("ACTIVE", "SUSPENDED"));
        assertTrue(canTransition("SUSPENDED", "ACTIVE"));
        assertTrue(canTransition("ACTIVE", "TERMINATED"));
        assertTrue(canTransition("ACTIVE", "EXPIRED"));
    }

    @Test
    void permitsCancellationOnlyBeforeActivation() throws Exception {
        assertTrue(canTransition("DRAFT", "CANCELLED"));
        assertTrue(canTransition("PENDING_APPROVAL", "CANCELLED"));
        assertFalse(canTransition("ACTIVE", "CANCELLED"));
        assertFalse(canTransition("SUSPENDED", "CANCELLED"));
    }

    @Test
    void rejectsSkippingApprovalAndLeavingTerminalStates() throws Exception {
        assertFalse(canTransition("DRAFT", "ACTIVE"));
        assertFalse(canTransition("DRAFT", "TERMINATED"));
        assertFalse(canTransition("TERMINATED", "ACTIVE"));
        assertFalse(canTransition("CANCELLED", "PENDING_APPROVAL"));
        assertFalse(canTransition("EXPIRED", "ACTIVE"));
        assertFalse(canTransition("ACTIVE", "ACTIVE"));
    }

    private static boolean canTransition(String from, String to) throws Exception {
        Class<?> statusType = Class.forName(STATUS_CLASS);
        Class<?> machineType = Class.forName(MACHINE_CLASS);
        Method method = machineType.getMethod("canTransition", statusType, statusType);
        Object source = enumConstant(statusType, from);
        Object target = enumConstant(statusType, to);
        return (boolean) method.invoke(null, source, target);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumConstant(Class<?> type, String name) {
        return Enum.valueOf((Class<? extends Enum>) type.asSubclass(Enum.class), name);
    }
}
