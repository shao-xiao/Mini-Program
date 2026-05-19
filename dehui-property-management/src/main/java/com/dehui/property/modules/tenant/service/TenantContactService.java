package com.dehui.property.modules.tenant.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.mobile.entity.TenantContact;
import com.dehui.property.modules.mobile.repository.TenantContactRepository;
import com.dehui.property.modules.tenant.dto.TenantContactRequest;
import com.dehui.property.modules.tenant.dto.TenantContactResponse;
import com.dehui.property.modules.tenant.entity.Tenant;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantContactService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TenantContactRepository tenantContactRepository;
    private final TenantRepository tenantRepository;

    public Result<List<TenantContactResponse>> listByTenant(Long tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            return Result.error("租户不存在");
        }
        List<TenantContactResponse> contacts = tenantContactRepository.findByTenantIdOrderByIsPrimaryDescIdAsc(tenantId)
                .stream()
                .map(contact -> toResponse(contact, null))
                .toList();
        return Result.success(contacts);
    }

    @Transactional
    public Result<TenantContactResponse> save(Long tenantId, TenantContactRequest request) {
        if (!tenantRepository.existsById(tenantId)) {
            return Result.error("租户不存在");
        }

        TenantContact contact = tenantContactRepository.findByPhone(normalizePhone(request.getPhone()))
                .orElseGet(TenantContact::new);
        boolean isNew = contact.getId() == null;
        contact.setTenantId(tenantId);
        contact.setName(request.getName().trim());
        contact.setPhone(normalizePhone(request.getPhone()));
        contact.setRole(defaultRole(request.getRole()));
        contact.setIsPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        contact.setStatus(defaultStatus(request.getStatus()));

        String initialPassword = null;
        if (isNew || isBlank(contact.getPassword())) {
            initialPassword = generatePassword();
            contact.setPassword(initialPassword);
            contact.setRequirePasswordReset(Boolean.TRUE);
        }

        TenantContact saved = tenantContactRepository.save(contact);
        enforceSinglePrimary(tenantId, saved);
        return Result.success(toResponse(saved, initialPassword));
    }

    @Transactional
    public Result<TenantContactResponse> deactivate(Long contactId) {
        return tenantContactRepository.findById(contactId)
                .map(contact -> {
                    contact.setStatus("INACTIVE");
                    TenantContact saved = tenantContactRepository.save(contact);
                    return Result.success(toResponse(saved, null));
                })
                .orElseGet(() -> Result.error("联系人不存在"));
    }

    @Transactional
    public Result<TenantContactResponse> resetPassword(Long contactId) {
        return tenantContactRepository.findById(contactId)
                .map(contact -> {
                    String initialPassword = generatePassword();
                    contact.setPassword(initialPassword);
                    contact.setRequirePasswordReset(Boolean.TRUE);
                    contact.setStatus(defaultStatus(contact.getStatus()));
                    TenantContact saved = tenantContactRepository.save(contact);
                    return Result.success(toResponse(saved, initialPassword));
                })
                .orElseGet(() -> Result.error("联系人不存在"));
    }

    @Transactional
    public TenantContactResponse syncPrimaryContact(Long tenantId, String name, String phone) {
        if (tenantId == null || isBlank(name) || isBlank(phone)) {
            return null;
        }

        String normalizedPhone = normalizePhone(phone);
        TenantContact contact = tenantContactRepository.findByPhone(normalizedPhone)
                .orElseGet(TenantContact::new);
        boolean needsPassword = contact.getId() == null || isBlank(contact.getPassword());
        String initialPassword = needsPassword ? generatePassword() : null;

        contact.setTenantId(tenantId);
        contact.setName(name.trim());
        contact.setPhone(normalizedPhone);
        contact.setRole(defaultRole(contact.getRole()));
        contact.setIsPrimary(Boolean.TRUE);
        contact.setStatus(defaultStatus(contact.getStatus()));
        if (needsPassword) {
            contact.setPassword(initialPassword);
            contact.setRequirePasswordReset(Boolean.TRUE);
        }

        TenantContact saved = tenantContactRepository.save(contact);
        enforceSinglePrimary(tenantId, saved);
        return toResponse(saved, initialPassword);
    }

    public boolean matchesPassword(TenantContact contact, String password) {
        return contact != null
                && !isBlank(contact.getPassword())
                && contact.getPassword().equals(password);
    }

    public TenantContactResponse toResponse(TenantContact contact, String initialPassword) {
        TenantContactResponse response = new TenantContactResponse();
        response.setId(contact.getId());
        response.setTenantId(contact.getTenantId());
        tenantRepository.findById(contact.getTenantId())
                .ifPresent(tenant -> response.setTenantName(tenant.getTenantName()));
        response.setName(contact.getName());
        response.setPhone(contact.getPhone());
        response.setRole(contact.getRole());
        response.setIsPrimary(contact.getIsPrimary());
        response.setStatus(contact.getStatus());
        response.setRequirePasswordReset(contact.getRequirePasswordReset());
        response.setLastBindTime(contact.getLastBindTime());
        response.setLastBoundAt(formatDateTime(contact.getLastBindTime()));
        response.setLastLoginAt(contact.getLastLoginAt());
        response.setLastLoginAtText(formatDateTime(contact.getLastLoginAt()));
        response.setCreatedTime(contact.getCreatedTime());
        response.setUpdatedTime(contact.getUpdatedTime());
        response.setInitialPassword(initialPassword);
        return response;
    }

    private String generatePassword() {
        return String.valueOf(100000 + RANDOM.nextInt(900000));
    }

    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.trim();
    }

    private void enforceSinglePrimary(Long tenantId, TenantContact primary) {
        if (!Boolean.TRUE.equals(primary.getIsPrimary())) {
            return;
        }
        tenantContactRepository.findByTenantIdAndIsPrimary(tenantId, Boolean.TRUE)
                .stream()
                .filter(contact -> !contact.getId().equals(primary.getId()))
                .forEach(contact -> {
                    contact.setIsPrimary(Boolean.FALSE);
                    tenantContactRepository.save(contact);
                });
    }

    private String defaultRole(String role) {
        if (isBlank(role)) {
            return "OWNER_CONTACT";
        }
        String value = role.trim();
        return switch (value) {
            case "OWNER_CONTACT", "FINANCE_CONTACT", "ADMIN_CONTACT", "MAINTAIN_CONTACT" -> value;
            case "主联系人" -> "OWNER_CONTACT";
            case "财务联系人" -> "FINANCE_CONTACT";
            case "行政联系人" -> "ADMIN_CONTACT";
            case "报修联系人" -> "MAINTAIN_CONTACT";
            default -> "OWNER_CONTACT";
        };
    }

    private String defaultStatus(String status) {
        return isBlank(status) ? "ACTIVE" : status.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String formatDateTime(java.time.LocalDateTime value) {
        return value == null ? null : value.format(DATE_TIME_FORMATTER);
    }
}
