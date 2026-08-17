package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.security.AuthPrincipal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class CustomFieldAuthorizationService {

    public void requireView(
            AuthPrincipal principal,
            CustomFieldWorkspaceRegistry.Registration workspace
    ) {
        require(principal, List.of(workspace.viewPermission()), "无权查看该工作区的自定义字段");
    }

    public void requireManage(
            AuthPrincipal principal,
            CustomFieldWorkspaceRegistry.Registration workspace
    ) {
        require(principal, List.of(CustomFieldWorkspaceRegistry.MANAGE_PERMISSION), "缺少自定义字段管理权限");
        require(principal, workspace.valueUpdatePermissions().stream().toList(), "缺少当前工作区维护权限");
    }

    public void requireValueUpdate(
            AuthPrincipal principal,
            CustomFieldWorkspaceRegistry.Registration workspace
    ) {
        require(principal, workspace.valueUpdatePermissions().stream().toList(), "无权编辑该工作区的数据");
    }

    private void require(AuthPrincipal principal, List<String> anyOf, String message) {
        if (isSuperAdmin(principal)) return;
        if (principal == null || principal.permissions() == null
                || anyOf.stream().noneMatch(permission -> hasPermission(principal, permission))) {
            throw BusinessException.forbidden(message);
        }
    }

    private boolean isSuperAdmin(AuthPrincipal principal) {
        return principal != null && principal.roles() != null && principal.roles().contains("SUPER_ADMIN");
    }

    private boolean hasPermission(AuthPrincipal principal, String required) {
        List<String> actualPermissions = principal.permissions();
        if (actualPermissions.contains("*") || actualPermissions.contains(required)) return true;
        String mobileCapability = required.replace(':', '.');
        if (actualPermissions.contains(mobileCapability)) return true;
        if (!"SYSTEM".equals(principal.userType())
                && hasMobileWorkspaceCapability(actualPermissions, required)) return true;
        int index = required.lastIndexOf(':');
        while (index > 0) {
            if (actualPermissions.contains(required.substring(0, index) + ":*")) return true;
            index = required.lastIndexOf(':', index - 1);
        }
        return false;
    }

    private boolean hasMobileWorkspaceCapability(List<String> permissions, String required) {
        return switch (required) {
            case "room:view" -> any(permissions, "contract.view", "energy.company.view", "energy.staff.view");
            case "parking:view" -> any(permissions, "parking.mine.view", "parking.company.view", "parking.staff.view");
            case "meeting-room:view", "meeting-booking:view" ->
                    any(permissions, "meeting.guest.book", "meeting.book", "meeting.mine.view",
                            "meeting.company.view", "meeting.staff.view");
            case "visitor:view" ->
                    any(permissions, "visitor.create", "visitor.mine.view", "visitor.company.view", "visitor.staff.view");
            case "energy:view", "energy:meter:view" ->
                    any(permissions, "energy.company.view", "energy.staff.view");
            case "inspection:view" -> any(permissions, "inspection.staff.view");
            case "workorder:view" -> any(permissions, "workorder.view", "workorder.staff.view");
            case "investment-lead:view" ->
                    any(permissions, "investment.view", "investment.booking.create", "investment.lead.staff.view");
            default -> false;
        };
    }

    private boolean any(List<String> actual, String... expected) {
        for (String permission : expected) {
            if (actual.contains(permission)) return true;
        }
        return false;
    }
}
