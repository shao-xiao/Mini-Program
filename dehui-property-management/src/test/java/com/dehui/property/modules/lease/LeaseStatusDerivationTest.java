package com.dehui.property.modules.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class LeaseStatusDerivationTest {

    private static final String DERIVER_CLASS =
            "com.dehui.property.modules.lease.domain.service.LeaseStatusDeriver";

    @Test
    void derivesPendingPartialAndFullyActiveFromOccupiedRoomCount() throws Exception {
        assertEquals("PENDING_CHECKIN", derive(3, 0, 0));
        assertEquals("PARTIALLY_ACTIVE", derive(3, 1, 0));
        assertEquals("PARTIALLY_ACTIVE", derive(3, 2, 0));
        assertEquals("ACTIVE", derive(3, 3, 0));
    }

    @Test
    void partialCheckoutTakesPrecedenceOverRemainingActiveRooms() throws Exception {
        assertEquals("PARTIALLY_TERMINATED", derive(3, 2, 1));
        assertEquals("PARTIALLY_TERMINATED", derive(3, 1, 2));
        assertEquals("TERMINATED", derive(3, 0, 3));
    }

    @Test
    void statusIsDerivedFromCountsRatherThanLastRoomAction() throws Exception {
        assertEquals("PARTIALLY_ACTIVE", derive(4, 1, 0));
        assertEquals("PARTIALLY_TERMINATED", derive(4, 1, 1));
        assertEquals("PARTIALLY_TERMINATED", derive(4, 0, 1));
    }

    private static String derive(int totalRooms, int activeRooms, int terminatedRooms) throws Exception {
        Class<?> deriverType = Class.forName(DERIVER_CLASS);
        Method derive = deriverType.getMethod("derive", int.class, int.class, int.class);
        Object status = derive.invoke(null, totalRooms, activeRooms, terminatedRooms);
        return ((Enum<?>) status).name();
    }
}
