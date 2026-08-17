package com.dehui.property.modules.customfield.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class CustomFieldServerQueryAdapterRegistry {

    private static final Pattern TRUSTED_EXPRESSION =
            Pattern.compile("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$");

    private final Map<String, Adapter> adapters;

    public CustomFieldServerQueryAdapterRegistry(CustomFieldWorkspaceRegistry workspaceRegistry) {
        Map<String, Adapter> entries = new LinkedHashMap<>();
        register(entries, "rooms", "r.id");
        register(entries, "equipment", "e.id");
        register(entries, "leases", "l.id");
        register(entries, "fee-rules", "fr.id");
        register(entries, "billing-plans", "plan_record.id");
        register(entries, "investment-leads", "lead_record.id");
        register(entries, "investment-contents", "d.id");
        register(entries, "investment-listings", "l.id");
        register(entries, "bills", "b.id");
        register(entries, "parking-spaces", "s.id");
        register(entries, "workorders", "w.id");
        register(entries, "inspections", "i.id");
        register(entries, "energy-meters", "m.id");
        register(entries, "energy-records", "er.id");
        register(entries, "mobile-users", "mu.id");

        Set<String> serverWorkspaces = workspaceRegistry.registrations().stream()
                .filter(registration -> registration.queryMode() == CustomFieldWorkspaceRegistry.QueryMode.SERVER)
                .map(CustomFieldWorkspaceRegistry.Registration::workspaceKey)
                .collect(Collectors.toUnmodifiableSet());
        if (!entries.keySet().equals(serverWorkspaces)) {
            throw new IllegalStateException("Custom-field server query adapters do not match the workspace registry");
        }
        adapters = Map.copyOf(entries);
    }

    public Adapter require(String workspaceKey) {
        Adapter adapter = adapters.get(workspaceKey);
        if (adapter == null) {
            throw new IllegalArgumentException("Custom-field server query is not registered: " + workspaceKey);
        }
        return adapter;
    }

    public Set<String> workspaceKeys() {
        return adapters.keySet();
    }

    private static void register(Map<String, Adapter> entries, String workspaceKey, String entityIdExpression) {
        if (!TRUSTED_EXPRESSION.matcher(entityIdExpression).matches()) {
            throw new IllegalArgumentException("Unsafe custom-field entity expression: " + workspaceKey);
        }
        if (entries.putIfAbsent(workspaceKey, new Adapter(workspaceKey, entityIdExpression)) != null) {
            throw new IllegalStateException("Duplicate custom-field server query adapter: " + workspaceKey);
        }
    }

    public record Adapter(String workspaceKey, String entityIdExpression) {
    }
}
