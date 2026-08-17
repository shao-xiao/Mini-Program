package com.dehui.property.modules.mobile.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The single translation boundary between explicit staff-mobile permission
 * codes and mobile capabilities. Role names and desktop business permissions
 * are deliberately not accepted here.
 */
public final class MobileCapabilityMapper {

    public static final List<String> STAFF_BASE_CAPABILITIES = List.of(
            "announcement.view",
            "investment.view"
    );

    private MobileCapabilityMapper() {
    }

    public static List<String> fromPermissionCodes(List<String> permissionCodes) {
        Set<String> permissions = permissionCodes == null
                ? Set.of()
                : permissionCodes.stream()
                        .filter(code -> code != null && !code.isBlank())
                        .map(String::trim)
                        .collect(java.util.stream.Collectors.toSet());
        if (!has(permissions, "staff-mobile:access", "staff-mobile:*", "*")) {
            return List.of();
        }
        LinkedHashSet<String> capabilities = new LinkedHashSet<>(STAFF_BASE_CAPABILITIES);

        if (has(permissions,
                "staff-mobile:workorder:view", "staff-mobile:workorder:*",
                "staff-mobile:*", "*")) {
            capabilities.add("workorder.staff.view");
        }
        if (has(permissions,
                "staff-mobile:workorder:write", "staff-mobile:workorder:*",
                "staff-mobile:*", "*")) {
            capabilities.add("workorder.staff.start");
            capabilities.add("workorder.staff.complete");
        }

        if (has(permissions,
                "staff-mobile:inspection:view", "staff-mobile:inspection:*",
                "staff-mobile:*", "*")) {
            capabilities.add("inspection.staff.view");
        }
        if (has(permissions,
                "staff-mobile:inspection:write", "staff-mobile:inspection:*",
                "staff-mobile:*", "*")) {
            capabilities.add("inspection.staff.create");
            capabilities.add("inspection.staff.update");
        }

        if (has(permissions,
                "staff-mobile:energy:view", "staff-mobile:energy:*",
                "staff-mobile:*", "*")) {
            capabilities.add("energy.staff.view");
        }
        if (has(permissions,
                "staff-mobile:energy:write", "staff-mobile:energy:*",
                "staff-mobile:*", "*")) {
            capabilities.add("energy.staff.reading.create");
        }

        if (has(permissions,
                "staff-mobile:meeting:view", "staff-mobile:meeting:*",
                "staff-mobile:*", "*")) {
            capabilities.add("meeting.staff.view");
        }
        if (has(permissions,
                "staff-mobile:meeting:write", "staff-mobile:meeting:*",
                "staff-mobile:*", "*")) {
            capabilities.add("meeting.staff.manage");
            capabilities.add("meeting.staff.confirm");
            capabilities.add("meeting.staff.cancel");
            capabilities.add("meeting.staff.complete");
        }

        if (has(permissions,
                "staff-mobile:visitor:view", "staff-mobile:visitor:*",
                "staff-mobile:*", "*")) {
            capabilities.add("visitor.staff.view");
        }
        if (has(permissions,
                "staff-mobile:visitor:write", "staff-mobile:visitor:*",
                "staff-mobile:*", "*")) {
            capabilities.add("visitor.staff.verify");
        }

        if (has(permissions,
                "staff-mobile:investment:view", "staff-mobile:investment:*",
                "staff-mobile:*", "*")) {
            capabilities.add("investment.lead.staff.view");
        }
        if (has(permissions,
                "staff-mobile:investment:write", "staff-mobile:investment:*",
                "staff-mobile:*", "*")) {
            capabilities.add("investment.lead.staff.update");
            capabilities.add("investment.lead.staff.follow");
            capabilities.add("investment.lead.staff.convert");
        }
        return List.copyOf(capabilities);
    }

    public static boolean hasWorkbenchAccess(List<String> permissionCodes) {
        if (permissionCodes == null) return false;
        Set<String> permissions = permissionCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toSet());
        return has(permissions, "staff-mobile:access", "staff-mobile:*", "*");
    }

    private static boolean has(Set<String> permissions, String... codes) {
        for (String code : codes) {
            if (permissions.contains(code)) return true;
        }
        return false;
    }
}
