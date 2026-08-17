package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.CompiledQuery;
import com.dehui.property.modules.customfield.application.CustomFieldQueryCompiler.NativeOrderPolicy;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository;
import org.springframework.stereotype.Service;

@Service
public class CustomFieldQueryService {

    private final CustomFieldWorkspaceRegistry registry;
    private final CustomFieldServerQueryAdapterRegistry adapterRegistry;
    private final CustomFieldRepository repository;
    private final CustomFieldQueryCompiler compiler;

    public CustomFieldQueryService(
            CustomFieldWorkspaceRegistry registry,
            CustomFieldServerQueryAdapterRegistry adapterRegistry,
            CustomFieldRepository repository,
            CustomFieldQueryCompiler compiler
    ) {
        this.registry = registry;
        this.adapterRegistry = adapterRegistry;
        this.repository = repository;
        this.compiler = compiler;
    }

    public CompiledQuery compile(
            String workspaceKey,
            String customFilters,
            String groupBy,
            String sorts,
            NativeOrderPolicy nativeOrderPolicy
    ) {
        CustomFieldWorkspaceRegistry.Registration workspace = registry.require(workspaceKey);
        if (workspace.queryMode() != CustomFieldWorkspaceRegistry.QueryMode.SERVER) {
            throw new BusinessException(400, "Custom-field server query is not enabled for this workspace");
        }
        CustomFieldServerQueryAdapterRegistry.Adapter adapter = adapterRegistry.require(workspaceKey);
        return compiler.compile(
                adapter.entityIdExpression(),
                workspace.entityType(),
                repository.findDefinitions(workspaceKey, true),
                customFilters,
                groupBy,
                sorts,
                nativeOrderPolicy
        );
    }
}
