package com.dehui.property.modules.customfield.application;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class CustomFieldWorkspaceRegistry {

    public static final String VIEW_PERMISSION = "custom-field:view";
    public static final String MANAGE_PERMISSION = "custom-field:manage";
    private static final int DEFAULT_MAXIMUM_CUSTOM_FIELDS = 50;
    private static final Pattern IDENTIFIER = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final Map<String, Registration> registrations;

    public CustomFieldWorkspaceRegistry() {
        Map<String, Registration> entries = new LinkedHashMap<>();
        defaultRegistrations().forEach(registration -> register(entries, registration));
        registrations = Map.copyOf(entries);
    }

    public Registration require(String workspaceKey) {
        Registration registration = registrations.get(workspaceKey);
        if (registration == null) {
            throw new IllegalArgumentException("Unsupported custom-field workspace: " + workspaceKey);
        }
        return registration;
    }

    public boolean supports(String workspaceKey) {
        return registrations.containsKey(workspaceKey);
    }

    public Collection<Registration> registrations() {
        return registrations.values();
    }

    private static void register(Map<String, Registration> entries, Registration registration) {
        validate(registration);
        if (entries.putIfAbsent(registration.workspaceKey(), registration) != null) {
            throw new IllegalStateException("Duplicate custom-field workspace: " + registration.workspaceKey());
        }
        boolean duplicateEntityType = entries.values().stream()
                .filter(entry -> entry != registration)
                .anyMatch(entry -> entry.entityType().equals(registration.entityType()));
        if (duplicateEntityType) {
            throw new IllegalStateException("Duplicate custom-field entity type: " + registration.entityType());
        }
    }

    private static void validate(Registration registration) {
        if (!registration.workspaceKey().matches("^[a-z][a-z0-9-]*$")) {
            throw new IllegalArgumentException("Invalid workspace key: " + registration.workspaceKey());
        }
        if (!IDENTIFIER.matcher(registration.entityType()).matches()
                || !IDENTIFIER.matcher(registration.tableName()).matches()
                || !IDENTIFIER.matcher(registration.idColumn()).matches()) {
            throw new IllegalArgumentException("Unsafe custom-field registry identifier: " + registration.workspaceKey());
        }
        if (!registration.viewPermission().contains(":")) {
            throw new IllegalArgumentException("Missing view permission: " + registration.workspaceKey());
        }
        if (registration.valueUpdatePermissions().isEmpty()
                || registration.valueUpdatePermissions().stream().anyMatch(permission -> !permission.contains(":"))) {
            throw new IllegalArgumentException("Missing value update permission: " + registration.workspaceKey());
        }
    }

    private static List<Registration> defaultRegistrations() {
        return List.of(
                entry("buildings", "building", "building", "building:view", "building:update", QueryMode.CLIENT),
                entry("floors", "building_floor", "building_floor", "floor:view", "floor:update", QueryMode.CLIENT),
                entry("rooms", "building_room", "building_room", "room:view", "room:update", QueryMode.SERVER),
                entry("equipment", "equipment", "equipment", "asset:view", "asset:update", QueryMode.SERVER),
                entry("tenants", "tenant", "tenant", "tenant:view", "tenant:update", QueryMode.CLIENT),
                entry("leases", "lease", "lease", "lease:view", "lease:update", QueryMode.SERVER),
                entry("fee-rules", "fee_rule", "fee_rule", "fee-rule:view", "fee-rule:update", QueryMode.SERVER),
                entry("billing-plans", "billing_plan", "billing_plan", "billing-plan:view", "billing-plan:update", QueryMode.SERVER),
                entry("investment-leads", "investment_lead", "investment_lead", "investment-lead:view", "investment-lead:follow", QueryMode.SERVER),
                entry("investment-contents", "investment_content", "investment_display", "investment-content:view", "investment-content:update", QueryMode.SERVER),
                entry("investment-listings", "investment_listing", "investment_room_listing", "investment-listing:view", "investment-listing:update", QueryMode.SERVER),
                entry("contracts", "contract", "contract", "contract:view", "contract:update", QueryMode.CLIENT),
                entry("bills", "bill", "bill", "bill:view", "bill:update", QueryMode.SERVER),
                entry("parking-spaces", "parking_space", "parking_space", "parking:view", "parking:update", QueryMode.SERVER),
                entry("parking-assignments", "parking_assignment", "parking_assignment", "parking:view", "parking:update", QueryMode.CLIENT),
                entry("parking-bills", "parking_bill", "parking_bill", "parking-bill:view", "parking-bill:update", QueryMode.CLIENT),
                entry("meeting-rooms", "meeting_room", "meeting_room", "meeting-room:view", "meeting-room:update", QueryMode.CLIENT),
                entry("meeting-bookings", "meeting_booking", "meeting_booking", "meeting-booking:view", "meeting-booking:confirm", QueryMode.CLIENT),
                entry("workorders", "work_order", "work_order", "workorder:view", "workorder:update", QueryMode.SERVER),
                entry("visitors", "visitor_appointment", "visitor_appointment", "visitor:view", "visitor:update", QueryMode.CLIENT),
                entry("inspections", "inspection_record", "inspection_record", "inspection:view", "inspection:update", QueryMode.SERVER),
                entry("announcements", "notice", "notice", "announcement:view", "announcement:update", QueryMode.CLIENT),
                entry("energy-meters", "energy_meter", "energy_meter", "energy:meter:view", "energy:meter:update", QueryMode.SERVER),
                entry("energy-records", "energy_reading", "energy_reading", "energy:view", "energy:reading:update", QueryMode.SERVER),
                entry("system-users", "system_user", "sys_user", "system:user:view", "system:user:update", QueryMode.CLIENT),
                entry("mobile-users", "mobile_user", "mobile_user", "mobile:view", "mobile:update", QueryMode.SERVER),
                entry("system-roles", "system_role", "sys_role", "system:role:view", "system:role:update", QueryMode.CLIENT)
        );
    }

    private static Registration entry(
            String workspaceKey,
            String entityType,
            String tableName,
            String viewPermission,
            String valueUpdatePermission,
            QueryMode queryMode
    ) {
        return new Registration(
                workspaceKey,
                entityType,
                tableName,
                "id",
                viewPermission,
                Set.of(valueUpdatePermission),
                queryMode,
                DEFAULT_MAXIMUM_CUSTOM_FIELDS
        );
    }

    public enum QueryMode {
        CLIENT,
        SERVER
    }

    public record Registration(
            String workspaceKey,
            String entityType,
            String tableName,
            String idColumn,
            String viewPermission,
            Set<String> valueUpdatePermissions,
            QueryMode queryMode,
            int maximumCustomFields
    ) {
        public Registration {
            valueUpdatePermissions = Set.copyOf(valueUpdatePermissions);
        }
    }
}
