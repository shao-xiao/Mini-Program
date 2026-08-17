-- Read-only diagnostics for duplicate mini-program identities.
-- Run against the target database before any manual merge.

-- 1. Active mobile users sharing one normalized phone number.
--    Treat +86 / 0086 / spaces and separators as the same mainland number.
WITH normalized_mobile_phone AS (
    SELECT id,
           CASE
             WHEN REGEXP_REPLACE(COALESCE(phone, ''), '[^0-9]', '') REGEXP '^[0-9]{11}$'
               THEN REGEXP_REPLACE(phone, '[^0-9]', '')
             WHEN REGEXP_REPLACE(COALESCE(phone, ''), '[^0-9]', '') REGEXP '^(86|0086)[0-9]{11}$'
               THEN RIGHT(REGEXP_REPLACE(phone, '[^0-9]', ''), 11)
             ELSE NULL
           END AS normalized_phone
    FROM mobile_user
    WHERE deleted = 0
)
SELECT normalized_phone,
       COUNT(*) AS user_count,
       GROUP_CONCAT(id ORDER BY id) AS mobile_user_ids
FROM normalized_mobile_phone
WHERE normalized_phone IS NOT NULL
GROUP BY normalized_phone
HAVING COUNT(*) > 1;

-- 2. Active WeChat identities sharing one app_id/openid pair.
SELECT app_id,
       openid,
       COUNT(*) AS identity_count,
       GROUP_CONCAT(id ORDER BY id) AS identity_ids,
       GROUP_CONCAT(mobile_user_id ORDER BY mobile_user_id) AS mobile_user_ids
FROM wechat_identity
WHERE deleted = 0
GROUP BY app_id, openid
HAVING COUNT(*) > 1;

-- 3. One phone number owned by both an OpenID user and another mobile user.
SELECT wi.app_id,
       wi.openid,
       openid_user.id AS openid_mobile_user_id,
       phone_user.id AS phone_mobile_user_id,
       phone_user.phone
FROM wechat_identity wi
INNER JOIN mobile_user openid_user
        ON openid_user.id = wi.mobile_user_id
       AND openid_user.deleted = 0
INNER JOIN mobile_user phone_user
        ON phone_user.phone = openid_user.phone
       AND phone_user.id <> openid_user.id
       AND phone_user.deleted = 0
WHERE wi.deleted = 0
  AND openid_user.phone IS NOT NULL
  AND TRIM(openid_user.phone) <> ''
ORDER BY wi.app_id, wi.openid, phone_user.id;

-- 4. Tenant contacts actively bound to more than one mobile user.
SELECT tenant_contact_id,
       COUNT(DISTINCT mobile_user_id) AS mobile_user_count,
       GROUP_CONCAT(DISTINCT mobile_user_id ORDER BY mobile_user_id) AS mobile_user_ids
FROM mobile_user_bind
WHERE bind_type = 'TENANT'
  AND status = 'ENABLED'
  AND deleted = 0
  AND tenant_contact_id IS NOT NULL
GROUP BY tenant_contact_id
HAVING COUNT(DISTINCT mobile_user_id) > 1;

-- 5. Internal accounts actively bound to more than one mobile user.
SELECT sys_user_id,
       COUNT(DISTINCT mobile_user_id) AS mobile_user_count,
       GROUP_CONCAT(DISTINCT mobile_user_id ORDER BY mobile_user_id) AS mobile_user_ids
FROM mobile_user_bind
WHERE bind_type = 'INTERNAL'
  AND status = 'ENABLED'
  AND deleted = 0
  AND sys_user_id IS NOT NULL
GROUP BY sys_user_id
HAVING COUNT(DISTINCT mobile_user_id) > 1;

-- 5a. Every active INTERNAL binding that is not currently eligible for STAFF.
--     This is the production read-only scan that must be empty after users
--     complete official WeChat phone verification and stale bindings are closed.
WITH active_staff_binding AS (
    SELECT bind_record.id AS binding_id,
           bind_record.mobile_user_id,
           bind_record.sys_user_id,
           mobile.phone,
           mobile.phone_verified_at,
           mobile.phone_verification_source,
           mobile.status AS mobile_status,
           staff.phone AS staff_phone,
           staff.status AS staff_status,
           staff.mobile_enabled,
           CASE
             WHEN mobile.phone_verification_source <> 'WECHAT_API'
               THEN 'PHONE_SOURCE_NOT_WECHAT_API'
             WHEN mobile.phone_verified_at IS NULL
               THEN 'PHONE_NOT_VERIFIED'
             WHEN staff.id IS NULL OR staff.deleted <> 0
               THEN 'STAFF_MISSING_OR_DELETED'
             WHEN mobile.status NOT IN ('ACTIVE', 'ENABLED')
               THEN 'MOBILE_USER_DISABLED'
             WHEN staff.status NOT IN ('ACTIVE', 'ENABLED')
               THEN 'STAFF_DISABLED'
             WHEN staff.mobile_enabled <> 1
               THEN 'STAFF_MOBILE_DISABLED'
             WHEN REGEXP_REPLACE(COALESCE(mobile.phone, ''), '[^0-9]', '') = ''
               OR REGEXP_REPLACE(COALESCE(staff.phone, ''), '[^0-9]', '') = ''
               THEN 'PHONE_EMPTY'
             WHEN RIGHT(REGEXP_REPLACE(mobile.phone, '[^0-9]', ''), 11)
                <> RIGHT(REGEXP_REPLACE(staff.phone, '[^0-9]', ''), 11)
               THEN 'PHONE_MISMATCH'
             WHEN NOT EXISTS (
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
             ) THEN 'STAFF_MOBILE_PERMISSION_REVOKED'
             ELSE 'ELIGIBLE'
           END AS eligibility_reason
    FROM mobile_user_bind bind_record
    INNER JOIN mobile_user mobile
            ON mobile.id = bind_record.mobile_user_id
           AND mobile.deleted = 0
    LEFT JOIN sys_user staff
           ON staff.id = bind_record.sys_user_id
    WHERE bind_record.bind_type = 'INTERNAL'
      AND bind_record.status = 'ENABLED'
      AND bind_record.deleted = 0
)
SELECT binding_id,
       mobile_user_id,
       sys_user_id,
       eligibility_reason
FROM active_staff_binding
WHERE eligibility_reason <> 'ELIGIBLE'
ORDER BY mobile_user_id, binding_id;

-- 5b. Broken current identity pointers.
SELECT mobile.id AS mobile_user_id,
       mobile.current_bind_id,
       bind_record.mobile_user_id AS binding_mobile_user_id,
       bind_record.status AS binding_status,
       bind_record.deleted AS binding_deleted
FROM mobile_user mobile
LEFT JOIN mobile_user_bind bind_record
       ON bind_record.id = mobile.current_bind_id
WHERE mobile.deleted = 0
  AND mobile.current_bind_id IS NOT NULL
  AND (
      bind_record.id IS NULL
      OR bind_record.mobile_user_id <> mobile.id
      OR bind_record.status <> 'ENABLED'
      OR bind_record.deleted <> 0
  )
ORDER BY mobile.id;

-- 6. Legacy fake OpenID identities created by the old development login flow.
SELECT wi.id AS wechat_identity_id,
       wi.mobile_user_id,
       mu.code,
       mu.phone,
       mu.user_type,
       mu.status,
       wi.openid,
       wi.created_at
FROM wechat_identity wi
INNER JOIN mobile_user mu
        ON mu.id = wi.mobile_user_id
       AND mu.deleted = 0
WHERE wi.app_id = 'dehui-dev-miniprogram'
  AND wi.deleted = 0
ORDER BY wi.id;

-- 7. Phone-only users that require manual comparison with OpenID-only users.
SELECT mu.id AS mobile_user_id,
       mu.code,
       mu.phone,
       mu.user_type,
       mu.status,
       mub.tenant_id,
       mub.tenant_contact_id,
       mub.sys_user_id
FROM mobile_user mu
LEFT JOIN wechat_identity wi
       ON wi.mobile_user_id = mu.id
      AND wi.deleted = 0
LEFT JOIN mobile_user_bind mub
       ON mub.id = (
           SELECT b.id
           FROM mobile_user_bind b
           WHERE b.mobile_user_id = mu.id
             AND b.status = 'ENABLED'
             AND b.deleted = 0
           ORDER BY b.id DESC
           LIMIT 1
       )
WHERE mu.deleted = 0
  AND mu.phone IS NOT NULL
  AND TRIM(mu.phone) <> ''
  AND wi.id IS NULL
ORDER BY mu.id;

-- 8. OpenID users without a phone, for manual pairing only.
SELECT mu.id AS mobile_user_id,
       mu.code,
       mu.user_type,
       mu.status,
       wi.app_id,
       wi.openid,
       wi.unionid
FROM mobile_user mu
INNER JOIN wechat_identity wi
        ON wi.mobile_user_id = mu.id
       AND wi.deleted = 0
WHERE mu.deleted = 0
  AND (mu.phone IS NULL OR TRIM(mu.phone) = '')
ORDER BY mu.id, wi.id;

-- 9. Business records that must be reassigned if two users are merged.
-- Replace the two values below before running this read-only inventory.
SET @canonical_mobile_user_id = 0;
SET @duplicate_mobile_user_id = 0;

SELECT 'wechat_identity' AS source_table, COUNT(*) AS record_count
FROM wechat_identity
WHERE mobile_user_id = @duplicate_mobile_user_id AND deleted = 0
UNION ALL
SELECT 'mobile_user_bind', COUNT(*)
FROM mobile_user_bind
WHERE mobile_user_id = @duplicate_mobile_user_id AND deleted = 0
UNION ALL
SELECT 'meeting_booking', COUNT(*)
FROM meeting_booking
WHERE applicant_mobile_user_id = @duplicate_mobile_user_id AND deleted = 0
UNION ALL
SELECT 'work_order', COUNT(*)
FROM work_order
WHERE submitter_mobile_user_id = @duplicate_mobile_user_id AND deleted = 0
UNION ALL
SELECT 'investment_lead', COUNT(*)
FROM investment_lead
WHERE mobile_user_id = @duplicate_mobile_user_id
  AND deleted = 0
UNION ALL
SELECT 'notice_read_record', COUNT(*)
FROM notice_read_record
WHERE reader_type IN ('MOBILE', 'MOBILE_USER')
  AND reader_id = @duplicate_mobile_user_id
  AND deleted = 0;
