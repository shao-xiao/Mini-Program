package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.BusinessException;
import com.dehui.property.security.TokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MobileIdentityService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private final JdbcTemplate jdbcTemplate;
    private final TokenService tokenService;

    @Autowired
    public MobileIdentityService(JdbcTemplate jdbcTemplate, TokenService tokenService) {
        this.jdbcTemplate = jdbcTemplate;
        this.tokenService = tokenService;
    }

    public MobileIdentityService(JdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, null);
    }

    @Transactional
    public Resolution verifyPhoneAndResolve(Long mobileUserId, String rawPhone) {
        String phone = normalizePhone(rawPhone);
        if (phone.isBlank()) {
            throw new BusinessException(400, "微信未返回有效手机号");
        }
        Long resolvedMobileUserId = resolveVerifiedPhoneOwner(mobileUserId, phone);
        jdbcTemplate.update(
                """
                UPDATE mobile_user
                SET phone = ?, phone_verified_at = NOW(),
                    phone_verification_source = 'WECHAT_API',
                    last_login_at = NOW(), updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                phone,
                0L,
                resolvedMobileUserId
        );

        List<Map<String, Object>> matches = businessMatches(phone);
        List<Long> validBindIds = new ArrayList<>();
        for (Map<String, Object> match : matches) {
            validBindIds.add(ensureBinding(resolvedMobileUserId, match));
        }
        disableStaleBindings(resolvedMobileUserId, validBindIds);

        Long currentBindId = jdbcTemplate.queryForObject(
                "SELECT current_bind_id FROM mobile_user WHERE id = ? AND deleted = 0",
                Long.class,
                resolvedMobileUserId
        );
        Resolution resolution;
        if (currentBindId != null && validBindIds.contains(currentBindId)) {
            selectBinding(resolvedMobileUserId, currentBindId);
            resolution = new Resolution(
                    resolvedMobileUserId,
                    "BOUND",
                    validBindIds.size() > 1 ? issueCandidates(resolvedMobileUserId) : List.of()
            );
        } else if (validBindIds.size() == 1) {
            selectBinding(resolvedMobileUserId, validBindIds.getFirst());
            resolution = new Resolution(resolvedMobileUserId, "BOUND", List.of());
        } else {
            clearCurrentIdentity(resolvedMobileUserId);
            if (validBindIds.isEmpty()) {
                resolution = new Resolution(resolvedMobileUserId, "UNMATCHED", List.of());
            } else {
                resolution = new Resolution(
                        resolvedMobileUserId,
                        "PENDING_SELECTION",
                        issueCandidates(resolvedMobileUserId)
                );
            }
        }

        // Session revocation is part of the identity transaction. If Redis is
        // unavailable, the database changes must roll back so an old PUBLIC
        // token can never become STAFF after the cache recovers.
        revokeMobileSessions(mobileUserId);
        if (!Objects.equals(resolvedMobileUserId, mobileUserId)) {
            revokeMobileSessions(resolvedMobileUserId);
        }
        return resolution;
    }

    @Transactional
    public void touchLogin(Long mobileUserId) {
        jdbcTemplate.update(
                "UPDATE mobile_user SET last_login_at = NOW(), updated_by = ? WHERE id = ? AND deleted = 0",
                0L,
                mobileUserId
        );
    }

    @Transactional
    public Long consumeCandidate(Long mobileUserId, String rawToken) {
        String token = rawToken == null ? "" : rawToken.trim();
        if (token.isBlank()) {
            throw new BusinessException(400, "candidateToken不能为空");
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT candidate.id AS candidateId,
                       candidate.mobile_user_bind_id AS bindId
                FROM mobile_identity_candidate candidate
                WHERE candidate.mobile_user_id = ?
                  AND candidate.token_hash = ?
                  AND candidate.candidate_status = 'PENDING'
                  AND candidate.expires_at > NOW()
                  AND candidate.deleted = 0
                FOR UPDATE
                """,
                mobileUserId,
                hash(token)
        );
        if (rows.isEmpty()) {
            throw new BusinessException(400, "candidateToken无效、已过期或已使用");
        }
        Long candidateId = longValue(rows.getFirst().get("candidateId"));
        Long bindId = longValue(rows.getFirst().get("bindId"));
        if (!isBindingEligible(mobileUserId, bindId)) {
            jdbcTemplate.update(
                    "UPDATE mobile_identity_candidate SET candidate_status = 'EXPIRED', updated_by = ? WHERE id = ?",
                    0L,
                    candidateId
            );
            throw BusinessException.forbidden("候选身份已失效");
        }
        int consumed = jdbcTemplate.update(
                """
                UPDATE mobile_identity_candidate
                SET candidate_status = 'USED', used_at = NOW(), updated_by = ?
                WHERE id = ? AND candidate_status = 'PENDING' AND deleted = 0
                """,
                0L,
                candidateId
        );
        if (consumed != 1) {
            throw new BusinessException(400, "candidateToken已使用");
        }
        selectBinding(mobileUserId, bindId);
        jdbcTemplate.update(
                """
                UPDATE mobile_identity_candidate
                SET candidate_status = 'EXPIRED', updated_by = ?
                WHERE mobile_user_id = ?
                  AND id <> ?
                  AND candidate_status = 'PENDING'
                  AND deleted = 0
                """,
                0L,
                mobileUserId,
                candidateId
        );
        // Keep candidate consumption and old-session revocation atomic from
        // the caller's perspective; a Redis failure rolls the DB transaction
        // back instead of leaving an upgradeable pre-selection token alive.
        revokeMobileSessions(mobileUserId);
        return bindId;
    }

    @Transactional
    public void unbindCurrent(Long mobileUserId) {
        unbindCurrent(mobileUserId, null, "USER_UNBIND");
    }

    @Transactional
    public void unbindCurrent(Long mobileUserId, Long operatorSysUserId, String action) {
        Long currentBindId = jdbcTemplate.queryForObject(
                "SELECT current_bind_id FROM mobile_user WHERE id = ? AND deleted = 0",
                Long.class,
                mobileUserId
        );
        if (currentBindId != null) {
            jdbcTemplate.update(
                    """
                    UPDATE mobile_user_bind
                    SET status = 'DISABLED', updated_by = ?
                    WHERE id = ? AND mobile_user_id = ? AND deleted = 0
                    """,
                    0L,
                    currentBindId,
                    mobileUserId
            );
        }
        clearCurrentIdentity(mobileUserId);
        jdbcTemplate.update(
                """
                UPDATE mobile_user
                SET phone_verified_at = NULL,
                    phone_verification_source = 'UNVERIFIED',
                    updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                operatorSysUserId == null ? 0L : operatorSysUserId,
                mobileUserId
        );
        jdbcTemplate.update(
                """
                UPDATE mobile_identity_candidate
                SET candidate_status = 'EXPIRED', updated_by = ?
                WHERE mobile_user_id = ? AND candidate_status = 'PENDING' AND deleted = 0
                """,
                0L,
                mobileUserId
        );
        audit(mobileUserId, action, currentBindId, null, operatorSysUserId);
        revokeMobileSessions(mobileUserId);
    }

    @Transactional
    public int invalidateIneligibleStaffBindings(
            Collection<Long> sysUserIds,
            Long operatorSysUserId,
            String reason) {
        return invalidateStaffBindings(sysUserIds, operatorSysUserId, reason, false);
    }

    @Transactional
    public int invalidateAllStaffBindings(
            Long sysUserId,
            Long operatorSysUserId,
            String reason) {
        return invalidateStaffBindings(
                sysUserId == null ? List.of() : List.of(sysUserId),
                operatorSysUserId,
                reason,
                true
        );
    }

    private int invalidateStaffBindings(
            Collection<Long> sysUserIds,
            Long operatorSysUserId,
            String reason,
            boolean force) {
        if (sysUserIds == null || sysUserIds.isEmpty()) {
            return 0;
        }
        int invalidated = 0;
        for (Long sysUserId : sysUserIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList()) {
            List<Map<String, Object>> bindings = jdbcTemplate.queryForList(
                    """
                    SELECT bind_record.id AS bindId,
                           bind_record.mobile_user_id AS mobileUserId,
                           mobile.current_bind_id AS currentBindId,
                           mobile.phone,
                           mobile.phone_verified_at AS phoneVerifiedAt,
                           mobile.phone_verification_source AS phoneVerificationSource,
                           mobile.status AS mobileStatus,
                           staff.phone AS staffPhone,
                           staff.status AS staffStatus,
                           staff.mobile_enabled AS mobileEnabled,
                           EXISTS (
                             SELECT 1
                             FROM sys_user_role user_role
                             INNER JOIN sys_role role_record
                               ON role_record.id = user_role.role_id
                              AND role_record.deleted = 0
                              AND role_record.status IN ('ACTIVE', 'ENABLED')
                             INNER JOIN sys_role_permission role_permission
                               ON role_permission.role_id = role_record.id
                              AND role_permission.deleted = 0
                              AND role_permission.status IN ('ACTIVE', 'ENABLED')
                             INNER JOIN sys_permission permission_record
                               ON permission_record.id = role_permission.permission_id
                              AND permission_record.deleted = 0
                              AND permission_record.status IN ('ACTIVE', 'ENABLED')
                             WHERE user_role.user_id = staff.id
                               AND user_role.deleted = 0
                               AND user_role.status IN ('ACTIVE', 'ENABLED')
                               AND permission_record.code IN ('staff-mobile:access', 'staff-mobile:*', '*')
                           ) AS hasMobileWorkbenchAccess
                    FROM mobile_user_bind bind_record
                    INNER JOIN mobile_user mobile
                      ON mobile.id = bind_record.mobile_user_id
                     AND mobile.deleted = 0
                    LEFT JOIN sys_user staff
                      ON staff.id = bind_record.sys_user_id
                     AND staff.deleted = 0
                    WHERE bind_record.sys_user_id = ?
                      AND bind_record.bind_type = 'INTERNAL'
                      AND bind_record.status = 'ENABLED'
                      AND bind_record.deleted = 0
                    ORDER BY bind_record.id
                    FOR UPDATE
                    """,
                    sysUserId
            );
            for (Map<String, Object> binding : bindings) {
                if (!force && isEligibleStaffBinding(binding)) {
                    continue;
                }
                Long bindId = longValue(binding.get("bindId"));
                Long mobileUserId = longValue(binding.get("mobileUserId"));
                if (bindId == null || mobileUserId == null) {
                    continue;
                }
                int changed = jdbcTemplate.update(
                        """
                        UPDATE mobile_user_bind
                        SET status = 'DISABLED', updated_by = ?
                        WHERE id = ?
                          AND mobile_user_id = ?
                          AND bind_type = 'INTERNAL'
                          AND status = 'ENABLED'
                          AND deleted = 0
                        """,
                        operatorSysUserId == null ? 0L : operatorSysUserId,
                        bindId,
                        mobileUserId
                );
                if (changed != 1) {
                    continue;
                }
                jdbcTemplate.update(
                        """
                        UPDATE mobile_user
                        SET current_bind_id = NULL, user_type = 'PUBLIC', updated_by = ?
                        WHERE id = ? AND current_bind_id = ? AND deleted = 0
                        """,
                        operatorSysUserId == null ? 0L : operatorSysUserId,
                        mobileUserId,
                        bindId
                );
                jdbcTemplate.update(
                        """
                        UPDATE mobile_identity_candidate
                        SET candidate_status = 'EXPIRED', updated_by = ?
                        WHERE mobile_user_id = ?
                          AND candidate_status = 'PENDING'
                          AND deleted = 0
                        """,
                        operatorSysUserId == null ? 0L : operatorSysUserId,
                        mobileUserId
                );
                audit(
                        mobileUserId,
                        force ? "ADMIN_UNBIND_STAFF" : "STAFF_IDENTITY_INVALIDATED",
                        bindId,
                        null,
                        operatorSysUserId,
                        reason == null || reason.isBlank() ? "物业员工移动身份资格已失效" : reason
                );
                revokeMobileSessions(mobileUserId);
                invalidated++;
            }
        }
        return invalidated;
    }

    private boolean isEligibleStaffBinding(Map<String, Object> binding) {
        if (binding == null
                || binding.get("phoneVerifiedAt") == null
                || !"WECHAT_API".equalsIgnoreCase(
                        stringValue(binding.get("phoneVerificationSource")))
                || !isActiveStatus(binding.get("mobileStatus"))
                || !isActiveStatus(binding.get("staffStatus"))
                || !truthy(binding.get("mobileEnabled"))
                || !truthy(binding.get("hasMobileWorkbenchAccess"))) {
            return false;
        }
        String mobilePhone = normalizePhone(stringValue(binding.get("phone")));
        String staffPhone = normalizePhone(stringValue(binding.get("staffPhone")));
        return !mobilePhone.isBlank() && mobilePhone.equals(staffPhone);
    }

    @Transactional
    public List<Map<String, Object>> availableIdentities(Long mobileUserId) {
        return issueCandidates(mobileUserId);
    }

    public List<Map<String, Object>> adminIdentityCandidates(Long mobileUserId) {
        return jdbcTemplate.queryForList(
                """
                SELECT bind_record.id AS bindId,
                       CASE WHEN bind_record.bind_type = 'INTERNAL' THEN 'STAFF' ELSE 'TENANT' END AS userType,
                       tenant_record.name AS tenantName,
                       contact.name AS contactName,
                       CASE
                         WHEN contact.is_primary = 1 OR contact.role = 'RESPONSIBLE' THEN 'RESPONSIBLE'
                         WHEN contact.id IS NOT NULL THEN 'EMPLOYEE'
                         ELSE NULL
                       END AS contactRole,
                       staff.real_name AS staffRealName,
                       staff.id AS sysUserId,
                       bind_record.status,
                       CASE WHEN mobile.current_bind_id = bind_record.id THEN 1 ELSE 0 END AS current
                FROM mobile_user_bind bind_record
                INNER JOIN mobile_user mobile
                  ON mobile.id = bind_record.mobile_user_id
                 AND mobile.deleted = 0
                LEFT JOIN tenant_contact contact
                  ON contact.id = bind_record.tenant_contact_id
                 AND contact.deleted = 0
                 AND contact.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN tenant tenant_record
                  ON tenant_record.id = bind_record.tenant_id
                 AND tenant_record.deleted = 0
                 AND tenant_record.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN sys_user staff
                  ON staff.id = bind_record.sys_user_id
                 AND staff.deleted = 0
                 AND staff.status IN ('ACTIVE', 'ENABLED')
                 AND staff.mobile_enabled = 1
                WHERE bind_record.mobile_user_id = ?
                  AND bind_record.deleted = 0
                  AND mobile.phone_verified_at IS NOT NULL
                  AND (
                    (bind_record.bind_type = 'TENANT'
                      AND contact.id IS NOT NULL
                      AND tenant_record.id IS NOT NULL
                      AND %s = %s)
                    OR
                    (bind_record.bind_type = 'INTERNAL'
                      AND staff.id IS NOT NULL
                      AND mobile.phone_verification_source = 'WECHAT_API'
                      AND EXISTS (
                        SELECT 1
                        FROM sys_user_role user_role
                        INNER JOIN sys_role role_record
                          ON role_record.id = user_role.role_id
                         AND role_record.deleted = 0
                         AND role_record.status IN ('ACTIVE', 'ENABLED')
                        INNER JOIN sys_role_permission role_permission
                          ON role_permission.role_id = role_record.id
                         AND role_permission.deleted = 0
                         AND role_permission.status IN ('ACTIVE', 'ENABLED')
                        INNER JOIN sys_permission permission_record
                          ON permission_record.id = role_permission.permission_id
                         AND permission_record.deleted = 0
                         AND permission_record.status IN ('ACTIVE', 'ENABLED')
                        WHERE user_role.user_id = staff.id
                          AND user_role.deleted = 0
                          AND user_role.status IN ('ACTIVE', 'ENABLED')
                          AND permission_record.code IN ('staff-mobile:access', 'staff-mobile:*', '*')
                      )
                      AND %s = %s)
                  )
                ORDER BY current DESC, bind_record.id
                """.formatted(
                        normalizedPhoneSql("contact.phone"),
                        normalizedPhoneSql("mobile.phone"),
                        normalizedPhoneSql("staff.phone"),
                        normalizedPhoneSql("mobile.phone")
                ),
                mobileUserId
        );
    }

    @Transactional
    public void adminSelectIdentity(Long mobileUserId, Long bindId, Long operatorSysUserId) {
        Long previousBindId = jdbcTemplate.queryForObject(
                "SELECT current_bind_id FROM mobile_user WHERE id = ? AND deleted = 0 FOR UPDATE",
                Long.class,
                mobileUserId
        );
        boolean eligible = adminIdentityCandidates(mobileUserId).stream()
                .anyMatch(item -> bindId != null && bindId.equals(longValue(item.get("bindId"))));
        if (!eligible) {
            throw new BusinessException(400, "该身份不是当前验证手机号计算出的合法候选");
        }
        jdbcTemplate.update(
                "UPDATE mobile_user_bind SET status = 'ENABLED', updated_by = ? WHERE id = ? AND mobile_user_id = ? AND deleted = 0",
                operatorSysUserId,
                bindId,
                mobileUserId
        );
        selectBinding(mobileUserId, bindId);
        audit(mobileUserId, "ADMIN_SET_DEFAULT_IDENTITY", previousBindId, bindId, operatorSysUserId);
        revokeMobileSessions(mobileUserId);
    }

    @Transactional
    public void adminUpdateStatus(Long mobileUserId, String status, Long operatorSysUserId) {
        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();
        if (!List.of("ENABLED", "DISABLED").contains(normalizedStatus)) {
            throw new BusinessException(400, "小程序用户状态无效");
        }
        int updated = jdbcTemplate.update(
                "UPDATE mobile_user SET status = ?, updated_by = ? WHERE id = ? AND deleted = 0",
                normalizedStatus,
                operatorSysUserId == null ? 0L : operatorSysUserId,
                mobileUserId
        );
        if (updated != 1) {
            throw new BusinessException(404, "小程序用户不存在");
        }
        if ("DISABLED".equals(normalizedStatus)) {
            unbindCurrent(mobileUserId, operatorSysUserId, "ADMIN_DISABLE");
            return;
        }
        audit(
                mobileUserId,
                "ADMIN_ENABLE",
                null,
                null,
                operatorSysUserId,
                "小程序用户已启用，需重新验证手机号"
        );
    }

    public String bindingState(Long mobileUserId, String userType) {
        if ("TENANT".equals(userType) || "STAFF".equals(userType)) {
            return "BOUND";
        }
        List<Map<String, Object>> phoneRows = jdbcTemplate.queryForList(
                """
                SELECT phone,
                       phone_verified_at AS phoneVerifiedAt,
                       phone_verification_source AS phoneVerificationSource
                FROM mobile_user
                WHERE id = ? AND deleted = 0
                LIMIT 1
                """,
                mobileUserId
        );
        String phone = phoneRows.isEmpty() ? "" : stringValue(phoneRows.getFirst().get("phone"));
        if (phone.isBlank()) {
            return "UNVERIFIED";
        }
        if (phoneRows.getFirst().get("phoneVerifiedAt") == null
                || !"WECHAT_API".equalsIgnoreCase(
                        stringValue(phoneRows.getFirst().get("phoneVerificationSource")))) {
            return "REVERIFY_REQUIRED";
        }
        return activeIdentityRows(mobileUserId).isEmpty() ? "UNMATCHED" : "PENDING_SELECTION";
    }

    public static String normalizePhone(String rawPhone) {
        String digits = rawPhone == null ? "" : rawPhone.replaceAll("[^0-9]", "");
        if (digits.startsWith("0086") && digits.length() == 15) {
            digits = digits.substring(4);
        } else if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        return digits.matches("\\d{11}") ? digits : "";
    }

    private static String normalizedPhoneSql(String column) {
        String digits = "REGEXP_REPLACE(COALESCE(" + column + ", ''), '[^0-9]', '')";
        return """
                CASE
                  WHEN %1$s REGEXP '^[0-9]{11}$' THEN %1$s
                  WHEN %1$s REGEXP '^86[0-9]{11}$' THEN RIGHT(%1$s, 11)
                  WHEN %1$s REGEXP '^0086[0-9]{11}$' THEN RIGHT(%1$s, 11)
                  ELSE NULL
                END
                """.formatted(digits).trim();
    }

    private Long resolveVerifiedPhoneOwner(Long currentMobileUserId, String phone) {
        List<Map<String, Object>> ownerRows = jdbcTemplate.queryForList(
                """
                SELECT id, status, current_bind_id AS currentBindId
                FROM mobile_user
                WHERE %s = ?
                  AND id <> ?
                  AND deleted = 0
                ORDER BY id
                FOR UPDATE
                """.formatted(normalizedPhoneSql("phone")),
                phone,
                currentMobileUserId
        );
        if (ownerRows.isEmpty()) {
            return currentMobileUserId;
        }
        if (ownerRows.size() > 1) {
            throw phoneOwnerConflict("该手机号存在多个历史小程序账号，请管理员完成安全合并后重试");
        }

        Map<String, Object> owner = ownerRows.getFirst();
        Long ownerMobileUserId = longValue(owner.get("id"));
        if (ownerMobileUserId == null || !isActiveStatus(owner.get("status"))) {
            throw phoneOwnerConflict("该手机号对应的小程序账号已停用，请联系管理员处理");
        }

        List<Map<String, Object>> currentRows = jdbcTemplate.queryForList(
                """
                SELECT id, phone, phone_verified_at AS phoneVerifiedAt,
                       phone_verification_source AS phoneVerificationSource,
                       current_bind_id AS currentBindId, user_type AS userType, status
                FROM mobile_user
                WHERE id = ? AND deleted = 0
                LIMIT 1
                FOR UPDATE
                """,
                currentMobileUserId
        );
        if (currentRows.isEmpty()) {
            throw phoneOwnerConflict("当前微信已有关联身份，不能自动迁移，请联系管理员处理");
        }
        Map<String, Object> current = currentRows.getFirst();
        if (isProvisionalPublicUser(current)) {
            if (hasBusinessReferences(currentMobileUserId)) {
                throw phoneOwnerConflict("当前微信已有业务记录，不能自动迁移，请联系管理员处理");
            }
        } else {
            List<Map<String, Object>> staleBindings =
                    recoverableStaleStaffBindings(currentMobileUserId, current);
            if (staleBindings.isEmpty()) {
                throw phoneOwnerConflict("当前微信已有关联身份，不能自动迁移，请联系管理员处理");
            }
            if (hasNonBindingBusinessReferences(currentMobileUserId)) {
                throw phoneOwnerConflict("当前微信已有业务记录，不能自动迁移，请联系管理员处理");
            }
            closeStaleStaffBindings(currentMobileUserId, current, staleBindings);
        }

        List<Map<String, Object>> currentWechatRows = jdbcTemplate.queryForList(
                """
                SELECT id, app_id AS appId
                FROM wechat_identity
                WHERE mobile_user_id = ?
                  AND status IN ('ACTIVE', 'ENABLED')
                  AND deleted = 0
                ORDER BY id
                FOR UPDATE
                """,
                currentMobileUserId
        );
        if (currentWechatRows.size() != 1) {
            throw phoneOwnerConflict("当前微信技术身份不完整，请重新进入小程序后再试");
        }

        Long currentWechatIdentityId = longValue(currentWechatRows.getFirst().get("id"));
        String appId = stringValue(currentWechatRows.getFirst().get("appId"));
        jdbcTemplate.update(
                """
                UPDATE wechat_identity
                SET status = 'DISABLED', updated_by = ?,
                    remark = CONCAT(COALESCE(remark, ''), ' | 微信手机号重新验证后被替换')
                WHERE mobile_user_id = ?
                  AND app_id = ?
                  AND id <> ?
                  AND status IN ('ACTIVE', 'ENABLED')
                  AND deleted = 0
                """,
                0L,
                ownerMobileUserId,
                appId,
                currentWechatIdentityId
        );
        int transferred = jdbcTemplate.update(
                """
                UPDATE wechat_identity
                SET mobile_user_id = ?, status = 'ENABLED', updated_by = ?,
                    remark = CONCAT(COALESCE(remark, ''), ' | 微信手机号重新验证迁移')
                WHERE id = ?
                  AND mobile_user_id = ?
                  AND deleted = 0
                """,
                ownerMobileUserId,
                0L,
                currentWechatIdentityId,
                currentMobileUserId
        );
        if (transferred != 1) {
            throw phoneOwnerConflict("微信身份迁移失败，请重新进入小程序后再试");
        }

        jdbcTemplate.update(
                """
                UPDATE mobile_identity_candidate
                SET candidate_status = 'EXPIRED', updated_by = ?
                WHERE mobile_user_id = ?
                  AND candidate_status = 'PENDING'
                  AND deleted = 0
                """,
                0L,
                currentMobileUserId
        );
        jdbcTemplate.update(
                """
                UPDATE mobile_user
                SET status = 'DISABLED', current_bind_id = NULL, user_type = 'PUBLIC',
                    phone = NULL, phone_verified_at = NULL,
                    phone_verification_source = 'UNVERIFIED', updated_by = ?,
                    remark = CONCAT(COALESCE(remark, ''), ' | 已迁移至手机号原业务账号')
                WHERE id = ? AND deleted = 0
                """,
                0L,
                currentMobileUserId
        );
        audit(
                currentMobileUserId,
                "WECHAT_PHONE_IDENTITY_TRANSFER_OUT",
                null,
                null,
                null,
                "临时微信技术会话已停用，未迁移业务记录"
        );
        audit(
                ownerMobileUserId,
                "WECHAT_PHONE_IDENTITY_TRANSFER_IN",
                longValue(owner.get("currentBindId")),
                longValue(owner.get("currentBindId")),
                null,
                "微信官方手机号重新验证，业务账号已切换至当前微信"
        );
        return ownerMobileUserId;
    }

    private boolean isProvisionalPublicUser(Map<String, Object> row) {
        return isActiveStatus(row.get("status"))
                && "PUBLIC".equalsIgnoreCase(stringValue(row.get("userType")))
                && stringValue(row.get("phone")).isBlank()
                && row.get("phoneVerifiedAt") == null
                && longValue(row.get("currentBindId")) == null;
    }

    private List<Map<String, Object>> recoverableStaleStaffBindings(
            Long mobileUserId,
            Map<String, Object> mobileUser) {
        Long currentBindId = longValue(mobileUser.get("currentBindId"));
        String userType = stringValue(mobileUser.get("userType")).trim().toUpperCase();
        String verificationSource =
                stringValue(mobileUser.get("phoneVerificationSource")).trim().toUpperCase();
        if (!isActiveStatus(mobileUser.get("status"))
                || currentBindId == null
                || (!"STAFF".equals(userType) && !"INTERNAL".equals(userType))
                || "WECHAT_API".equals(verificationSource)) {
            return List.of();
        }

        List<Map<String, Object>> allBindings = jdbcTemplate.queryForList(
                """
                SELECT id, bind_type AS bindType, status
                FROM mobile_user_bind
                WHERE mobile_user_id = ?
                  AND deleted = 0
                ORDER BY id
                FOR UPDATE
                """,
                mobileUserId
        );
        List<Map<String, Object>> enabledBindings = allBindings.stream()
                .filter(row -> isActiveStatus(row.get("status")))
                .toList();
        boolean currentBindingPresent = enabledBindings.stream()
                .anyMatch(row -> currentBindId.equals(longValue(row.get("id"))));
        boolean onlyInternalBindingHistory = !allBindings.isEmpty()
                && allBindings.stream().allMatch(
                        row -> "INTERNAL".equalsIgnoreCase(stringValue(row.get("bindType"))));
        if (!currentBindingPresent
                || !onlyInternalBindingHistory
                || !activeIdentityRows(mobileUserId).isEmpty()) {
            return List.of();
        }
        return enabledBindings;
    }

    private void closeStaleStaffBindings(
            Long mobileUserId,
            Map<String, Object> mobileUser,
            List<Map<String, Object>> staleBindings) {
        int disabled = jdbcTemplate.update(
                """
                UPDATE mobile_user_bind
                SET status = 'DISABLED', updated_by = ?,
                    remark = CONCAT(COALESCE(remark, ''), ' | 微信官方手机号重新验证时关闭失效员工绑定')
                WHERE mobile_user_id = ?
                  AND bind_type = 'INTERNAL'
                  AND status = 'ENABLED'
                  AND deleted = 0
                """,
                0L,
                mobileUserId
        );
        if (disabled != staleBindings.size()) {
            throw phoneOwnerConflict("历史员工身份状态已变化，请重新进入小程序后再试");
        }
        clearCurrentIdentity(mobileUserId);
        jdbcTemplate.update(
                """
                UPDATE mobile_identity_candidate
                SET candidate_status = 'EXPIRED', updated_by = ?
                WHERE mobile_user_id = ?
                  AND candidate_status = 'PENDING'
                  AND deleted = 0
                """,
                0L,
                mobileUserId
        );
        audit(
                mobileUserId,
                "WECHAT_PHONE_STALE_STAFF_CLOSED",
                longValue(mobileUser.get("currentBindId")),
                null,
                null,
                "仅关闭无业务记录且已失效的历史员工绑定，准备迁移微信技术身份"
        );
    }

    private boolean hasBusinessReferences(Long mobileUserId) {
        List<Map<String, Object>> references = jdbcTemplate.queryForList(
                """
                SELECT 1 AS referenced
                FROM (
                    SELECT id FROM mobile_user_bind
                     WHERE mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM meeting_booking
                     WHERE applicant_mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM work_order
                     WHERE submitter_mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM visitor_appointment
                     WHERE applicant_mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM parking_assignment
                     WHERE mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM investment_lead
                     WHERE mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM file_upload
                     WHERE owner_mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM notice_read_record
                     WHERE reader_type IN ('MOBILE', 'MOBILE_USER')
                       AND reader_id = ?
                       AND deleted = 0
                ) business_reference
                LIMIT 1
                """,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId
        );
        return !references.isEmpty();
    }

    private boolean hasNonBindingBusinessReferences(Long mobileUserId) {
        List<Map<String, Object>> references = jdbcTemplate.queryForList(
                """
                SELECT 1 AS referenced
                FROM (
                    SELECT id FROM meeting_booking
                     WHERE applicant_mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM work_order
                     WHERE submitter_mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM visitor_appointment
                     WHERE applicant_mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM parking_assignment
                     WHERE mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM investment_lead
                     WHERE mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM file_upload
                     WHERE owner_mobile_user_id = ? AND deleted = 0
                    UNION ALL
                    SELECT id FROM notice_read_record
                     WHERE reader_type IN ('MOBILE', 'MOBILE_USER')
                       AND reader_id = ?
                       AND deleted = 0
                ) business_reference
                LIMIT 1
                """,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId,
                mobileUserId
        );
        return !references.isEmpty();
    }

    private BusinessException phoneOwnerConflict(String message) {
        return new BusinessException(409, message);
    }

    private boolean isActiveStatus(Object value) {
        String status = stringValue(value).trim().toUpperCase();
        return "ACTIVE".equals(status) || "ENABLED".equals(status);
    }

    private List<Map<String, Object>> businessMatches(String phone) {
        List<Map<String, Object>> result = new ArrayList<>(jdbcTemplate.queryForList(
                """
                SELECT 'TENANT' AS userType,
                       contact.id AS tenantContactId,
                       contact.tenant_id AS tenantId,
                       NULL AS sysUserId
                FROM tenant_contact contact
                INNER JOIN tenant tenant_record
                  ON tenant_record.id = contact.tenant_id
                 AND tenant_record.deleted = 0
                 AND tenant_record.status IN ('ACTIVE', 'ENABLED')
                WHERE %s = ?
                  AND contact.deleted = 0
                  AND contact.status IN ('ACTIVE', 'ENABLED')
                ORDER BY contact.is_primary DESC, contact.id
                """.formatted(normalizedPhoneSql("contact.phone")),
                phone
        ));
        List<Map<String, Object>> staffMatches = jdbcTemplate.queryForList(
                """
                SELECT 'STAFF' AS userType,
                       NULL AS tenantContactId,
                       NULL AS tenantId,
                       user_record.id AS sysUserId
                FROM sys_user user_record
                WHERE %s = ?
                  AND user_record.mobile_enabled = 1
                  AND user_record.status IN ('ACTIVE', 'ENABLED')
                  AND user_record.deleted = 0
                  AND EXISTS (
                    SELECT 1
                    FROM sys_user_role user_role
                    INNER JOIN sys_role role_record
                      ON role_record.id = user_role.role_id
                     AND role_record.deleted = 0
                     AND role_record.status IN ('ACTIVE', 'ENABLED')
                    INNER JOIN sys_role_permission role_permission
                      ON role_permission.role_id = role_record.id
                     AND role_permission.deleted = 0
                     AND role_permission.status IN ('ACTIVE', 'ENABLED')
                    INNER JOIN sys_permission permission_record
                      ON permission_record.id = role_permission.permission_id
                     AND permission_record.deleted = 0
                     AND permission_record.status IN ('ACTIVE', 'ENABLED')
                    WHERE user_role.user_id = user_record.id
                      AND user_role.deleted = 0
                      AND user_role.status IN ('ACTIVE', 'ENABLED')
                      AND permission_record.code IN ('staff-mobile:access', 'staff-mobile:*', '*')
                  )
                ORDER BY user_record.id
                """.formatted(normalizedPhoneSql("user_record.phone")),
                phone
        );
        result.addAll(staffMatches);
        return result;
    }

    private Long ensureBinding(Long mobileUserId, Map<String, Object> match) {
        String userType = stringValue(match.get("userType"));
        String bindType = "STAFF".equals(userType) ? "INTERNAL" : "TENANT";
        Long contactId = longValue(match.get("tenantContactId"));
        Long tenantId = longValue(match.get("tenantId"));
        Long sysUserId = longValue(match.get("sysUserId"));
        assertBusinessIdentityNotBoundElsewhere(
                mobileUserId,
                bindType,
                contactId,
                sysUserId
        );
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM mobile_user_bind
                WHERE mobile_user_id = ?
                  AND bind_type = ?
                  AND ((? IS NULL AND tenant_contact_id IS NULL) OR tenant_contact_id = ?)
                  AND ((? IS NULL AND sys_user_id IS NULL) OR sys_user_id = ?)
                  AND deleted = 0
                ORDER BY id DESC
                LIMIT 1
                """,
                mobileUserId,
                bindType,
                contactId,
                contactId,
                sysUserId,
                sysUserId
        );
        if (!existing.isEmpty()) {
            Long bindId = longValue(existing.getFirst().get("id"));
            jdbcTemplate.update(
                    """
                    UPDATE mobile_user_bind
                    SET tenant_id = ?, tenant_contact_id = ?, sys_user_id = ?,
                        status = 'ENABLED', updated_by = ?
                    WHERE id = ? AND deleted = 0
                    """,
                    tenantId,
                    contactId,
                    sysUserId,
                    0L,
                    bindId
            );
            return bindId;
        }
        jdbcTemplate.update(
                """
                INSERT INTO mobile_user_bind
                  (mobile_user_id, bind_type, tenant_id, tenant_contact_id, sys_user_id,
                   status, created_by, updated_by, remark)
                VALUES (?, ?, ?, ?, ?, 'ENABLED', ?, ?, ?)
                """,
                mobileUserId,
                bindType,
                tenantId,
                contactId,
                sysUserId,
                0L,
                0L,
                "微信已验证手机号自动匹配"
        );
        return jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM mobile_user_bind
                WHERE mobile_user_id = ?
                  AND bind_type = ?
                  AND ((? IS NULL AND tenant_contact_id IS NULL) OR tenant_contact_id = ?)
                  AND ((? IS NULL AND sys_user_id IS NULL) OR sys_user_id = ?)
                  AND status = 'ENABLED'
                  AND deleted = 0
                ORDER BY id DESC
                LIMIT 1
                """,
                Long.class,
                mobileUserId,
                bindType,
                contactId,
                contactId,
                sysUserId,
                sysUserId
        );
    }

    private void assertBusinessIdentityNotBoundElsewhere(
            Long mobileUserId,
            String bindType,
            Long tenantContactId,
            Long sysUserId) {
        List<Long> conflictingMobileUserIds;
        if ("INTERNAL".equals(bindType)) {
            conflictingMobileUserIds = jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT mobile_user_id
                    FROM mobile_user_bind
                    WHERE bind_type = 'INTERNAL'
                      AND sys_user_id = ?
                      AND mobile_user_id <> ?
                      AND status = 'ENABLED'
                      AND deleted = 0
                    FOR UPDATE
                    """,
                    Long.class,
                    sysUserId,
                    mobileUserId
            );
        } else {
            conflictingMobileUserIds = jdbcTemplate.queryForList(
                    """
                    SELECT DISTINCT mobile_user_id
                    FROM mobile_user_bind
                    WHERE bind_type = 'TENANT'
                      AND tenant_contact_id = ?
                      AND mobile_user_id <> ?
                      AND status = 'ENABLED'
                      AND deleted = 0
                    FOR UPDATE
                    """,
                    Long.class,
                    tenantContactId,
                    mobileUserId
            );
        }
        if (!conflictingMobileUserIds.isEmpty()) {
            throw phoneOwnerConflict(
                    "该业务身份已关联另一个小程序账号，请管理员完成安全合并后重试"
            );
        }
    }

    private void disableStaleBindings(Long mobileUserId, List<Long> validBindIds) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM mobile_user_bind
                WHERE mobile_user_id = ? AND status = 'ENABLED' AND deleted = 0
                """,
                mobileUserId
        );
        for (Map<String, Object> row : rows) {
            Long bindId = longValue(row.get("id"));
            if (!validBindIds.contains(bindId)) {
                jdbcTemplate.update(
                        "UPDATE mobile_user_bind SET status = 'DISABLED', updated_by = ? WHERE id = ? AND deleted = 0",
                        0L,
                        bindId
                );
            }
        }
    }

    private void selectBinding(Long mobileUserId, Long bindId) {
        String bindType = jdbcTemplate.queryForObject(
                """
                SELECT bind_type
                FROM mobile_user_bind
                WHERE id = ? AND mobile_user_id = ? AND status = 'ENABLED' AND deleted = 0
                """,
                String.class,
                bindId,
                mobileUserId
        );
        String userType = "INTERNAL".equals(bindType) ? "STAFF" : "TENANT";
        jdbcTemplate.update(
                """
                UPDATE mobile_user
                SET current_bind_id = ?, user_type = ?, updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                bindId,
                userType,
                0L,
                mobileUserId
        );
    }

    private void clearCurrentIdentity(Long mobileUserId) {
        jdbcTemplate.update(
                """
                UPDATE mobile_user
                SET current_bind_id = NULL, user_type = 'PUBLIC', updated_by = ?
                WHERE id = ? AND deleted = 0
                """,
                0L,
                mobileUserId
        );
    }

    private void audit(
            Long mobileUserId,
            String action,
            Long previousBindId,
            Long nextBindId,
            Long operatorSysUserId) {
        audit(
                mobileUserId,
                action,
                previousBindId,
                nextBindId,
                operatorSysUserId,
                "身份绑定状态变更"
        );
    }

    private void audit(
            Long mobileUserId,
            String action,
            Long previousBindId,
            Long nextBindId,
            Long operatorSysUserId,
            String detail) {
        jdbcTemplate.update(
                """
                INSERT INTO mobile_identity_audit
                  (mobile_user_id, action, previous_bind_id, next_bind_id, operator_sys_user_id, detail)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                mobileUserId,
                action,
                previousBindId,
                nextBindId,
                operatorSysUserId,
                detail
        );
    }

    private List<Map<String, Object>> issueCandidates(Long mobileUserId) {
        List<Map<String, Object>> identities = activeIdentityRows(mobileUserId);
        if (identities.size() <= 1) return List.of();
        jdbcTemplate.update(
                """
                UPDATE mobile_identity_candidate
                SET candidate_status = 'EXPIRED', updated_by = ?
                WHERE mobile_user_id = ? AND candidate_status = 'PENDING' AND deleted = 0
                """,
                0L,
                mobileUserId
        );
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> identity : identities) {
            Long bindId = longValue(identity.get("bindId"));
            String token = randomToken();
            jdbcTemplate.update(
                    """
                    INSERT INTO mobile_identity_candidate
                      (mobile_user_id, mobile_user_bind_id, token_hash, candidate_status,
                       expires_at, created_by, updated_by)
                    VALUES (?, ?, ?, 'PENDING', ?, ?, ?)
                    """,
                    mobileUserId,
                    bindId,
                    hash(token),
                    LocalDateTime.now().plusMinutes(10),
                    0L,
                    0L
            );
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            String userType = "INTERNAL".equals(stringValue(identity.get("bindType"))) ? "STAFF" : "TENANT";
            item.put("userType", userType);
            item.put("label", "TENANT".equals(userType)
                    ? stringValue(identity.get("tenantName"))
                    : stringValue(identity.get("staffRealName")));
            item.put("contactRole", "TENANT".equals(userType)
                    ? contactRole(identity)
                    : null);
            item.put("candidateToken", token);
            item.put("current", truthy(identity.get("currentIdentity")));
            result.add(item);
        }
        return List.copyOf(result);
    }

    private List<Map<String, Object>> activeIdentityRows(Long mobileUserId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT bind_record.id AS bindId,
                       bind_record.bind_type AS bindType,
                       tenant_record.name AS tenantName,
                       contact.name AS contactName,
                       contact.role AS contactRole,
                       contact.is_primary AS isPrimaryContact,
                       staff.real_name AS staffRealName,
                       staff.id AS sysUserId,
                       CASE WHEN mobile.current_bind_id = bind_record.id THEN 1 ELSE 0 END AS currentIdentity
                FROM mobile_user_bind bind_record
                INNER JOIN mobile_user mobile
                  ON mobile.id = bind_record.mobile_user_id
                 AND mobile.deleted = 0
                LEFT JOIN tenant_contact contact
                  ON contact.id = bind_record.tenant_contact_id
                 AND contact.deleted = 0
                 AND contact.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN tenant tenant_record
                  ON tenant_record.id = bind_record.tenant_id
                 AND tenant_record.deleted = 0
                 AND tenant_record.status IN ('ACTIVE', 'ENABLED')
                LEFT JOIN sys_user staff
                  ON staff.id = bind_record.sys_user_id
                 AND staff.deleted = 0
                 AND staff.status IN ('ACTIVE', 'ENABLED')
                 AND staff.mobile_enabled = 1
                WHERE bind_record.mobile_user_id = ?
                  AND bind_record.status = 'ENABLED'
                  AND bind_record.deleted = 0
                  AND mobile.phone_verified_at IS NOT NULL
                  AND (
                    (bind_record.bind_type = 'TENANT'
                      AND contact.id IS NOT NULL
                      AND tenant_record.id IS NOT NULL
                      AND %s = %s)
                    OR
                    (bind_record.bind_type = 'INTERNAL'
                      AND staff.id IS NOT NULL
                      AND mobile.phone_verification_source = 'WECHAT_API'
                      AND EXISTS (
                        SELECT 1
                        FROM sys_user_role user_role
                        INNER JOIN sys_role role_record
                          ON role_record.id = user_role.role_id
                         AND role_record.deleted = 0
                         AND role_record.status IN ('ACTIVE', 'ENABLED')
                        INNER JOIN sys_role_permission role_permission
                          ON role_permission.role_id = role_record.id
                         AND role_permission.deleted = 0
                         AND role_permission.status IN ('ACTIVE', 'ENABLED')
                        INNER JOIN sys_permission permission_record
                          ON permission_record.id = role_permission.permission_id
                         AND permission_record.deleted = 0
                         AND permission_record.status IN ('ACTIVE', 'ENABLED')
                        WHERE user_role.user_id = staff.id
                          AND user_role.deleted = 0
                          AND user_role.status IN ('ACTIVE', 'ENABLED')
                          AND permission_record.code IN ('staff-mobile:access', 'staff-mobile:*', '*')
                      )
                      AND %s = %s)
                  )
                ORDER BY currentIdentity DESC, bind_record.id
                """.formatted(
                        normalizedPhoneSql("contact.phone"),
                        normalizedPhoneSql("mobile.phone"),
                        normalizedPhoneSql("staff.phone"),
                        normalizedPhoneSql("mobile.phone")
                ),
                mobileUserId
        );
        return rows;
    }

    private boolean isBindingEligible(Long mobileUserId, Long bindId) {
        return activeIdentityRows(mobileUserId).stream()
                .anyMatch(row -> bindId != null && bindId.equals(longValue(row.get("bindId"))));
    }

    private String contactRole(Map<String, Object> identity) {
        if (truthy(identity.get("isPrimaryContact"))) return "RESPONSIBLE";
        String role = stringValue(identity.get("contactRole")).trim().toUpperCase();
        return List.of("OWNER", "MANAGER", "ADMIN", "PRIMARY", "RESPONSIBLE").contains(role)
                ? "RESPONSIBLE"
                : "EMPLOYEE";
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成候选身份令牌摘要", exception);
        }
    }

    private static Long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean booleanValue) return booleanValue;
        if (value instanceof Number number) return number.intValue() != 0;
        return "1".equals(stringValue(value)) || "TRUE".equalsIgnoreCase(stringValue(value));
    }

    private void revokeMobileSessions(Long mobileUserId) {
        if (tokenService != null && mobileUserId != null) {
            tokenService.revokeUserSessions(mobileUserId);
        }
    }

    public record Resolution(
            Long mobileUserId,
            String bindingState,
            List<Map<String, Object>> availableIdentities) {

        public Resolution(String bindingState, List<Map<String, Object>> availableIdentities) {
            this(null, bindingState, availableIdentities);
        }
    }
}
