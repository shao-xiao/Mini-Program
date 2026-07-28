package com.dehui.property.modules.visitor.controller;

import com.dehui.property.common.ApiResponse;
import com.dehui.property.common.BusinessException;
import com.dehui.property.common.ContactValidators;
import com.dehui.property.config.FileStorageProperties;
import com.dehui.property.security.RedisKeys;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Accepts a park-level visitor reservation without creating a mobile identity.
 *
 * <p>The authenticated tenant invitation flow remains in {@link VisitorController}.
 * Anonymous history and QR access are capability-scoped to the opaque, per-reservation
 * submission token retained by the submitting device. They never infer a tenant or
 * mobile-user relationship from the supplied phone number.</p>
 */
@RestController
public class PublicVisitorController {

    private static final String DEFAULT_TRUSTED_PROXIES = "127.0.0.1,::1,0:0:0:0:0:0:0:1";
    private static final String DEFAULT_UPLOAD_DIR = "uploads";
    private static final String SUBMISSION_TOKEN_HEADER = "X-Visitor-Submission-Token";
    private static final int HISTORY_LIMIT = 20;
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "visitorName",
            "visitorPhone",
            "visitTime",
            "visitCompany",
            "visitReason",
            "carPlateNo",
            "remark",
            "submissionToken"
    );
    private static final Pattern SUBMISSION_TOKEN = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");
    private static final String RATE_LIMIT_API = "mobile-public-visitors";
    private static final int IP_LIMIT = 5;
    private static final int PHONE_LIMIT = 3;
    private static final int SUBMISSION_TOKEN_LIMIT = 10;
    private static final Duration IP_WINDOW = Duration.ofMinutes(10);
    private static final Duration PHONE_WINDOW = Duration.ofHours(1);
    private static final Duration SUBMISSION_TOKEN_WINDOW = Duration.ofHours(1);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final Set<String> trustedProxyAddresses;
    private final Path uploadRoot;

    @Autowired
    public PublicVisitorController(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            @Value("${dehui.security.trusted-proxies:" + DEFAULT_TRUSTED_PROXIES + "}")
            String trustedProxyAddresses,
            FileStorageProperties fileStorageProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.trustedProxyAddresses = parseTrustedProxyAddresses(trustedProxyAddresses);
        String uploadDir = fileStorageProperties == null ? null : fileStorageProperties.uploadDir();
        this.uploadRoot = Path.of(
                uploadDir == null || uploadDir.isBlank() ? DEFAULT_UPLOAD_DIR : uploadDir
        ).toAbsolutePath().normalize();
    }

    public PublicVisitorController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate) {
        this(jdbcTemplate, redisTemplate, DEFAULT_TRUSTED_PROXIES, new FileStorageProperties(DEFAULT_UPLOAD_DIR));
    }

    public PublicVisitorController(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            String trustedProxyAddresses
    ) {
        this(jdbcTemplate, redisTemplate, trustedProxyAddresses, new FileStorageProperties(DEFAULT_UPLOAD_DIR));
    }

    @PostMapping("/mobile/public/visitors")
    @Transactional
    public ApiResponse<Map<String, Object>> create(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request
    ) {
        Map<String, Object> input = body == null ? Map.of() : body;
        rejectUnknownFields(input);

        String submissionToken = requiredText(input, "submissionToken", 128, "缺少预约提交令牌");
        if (!SUBMISSION_TOKEN.matcher(submissionToken).matches()) {
            throw new BusinessException(400, "预约提交令牌格式不正确");
        }
        String visitorName = requiredText(input, "visitorName", 64, "请填写访客姓名");
        String visitorPhone = normalizePhone(requiredText(input, "visitorPhone", 64, "请填写访客手机号"));
        String visitReason = requiredText(input, "visitReason", 500, "请填写来访事由");
        LocalDateTime visitTime = visitTime(requiredText(input, "visitTime", 32, "请选择来访时间"));
        String visitCompany = optionalText(input, "visitCompany", 128);
        String carPlateNo = optionalText(input, "carPlateNo", 32);
        String remark = optionalText(input, "remark", 500);
        String code = appointmentCode(submissionToken);
        Timestamp visitTimestamp = Timestamp.valueOf(visitTime);

        enforceRateLimit(clientAddress(request), visitorPhone, submissionToken);
        jdbcTemplate.update(
                """
                INSERT INTO visitor_appointment (
                    code, tenant_id, applicant_mobile_user_id,
                    visitor_name, visitor_phone, car_plate_no,
                    visit_company, visit_reason, visit_time,
                    appointment_status, status, created_by, updated_by, remark
                )
                VALUES (
                    ?, NULL, NULL,
                    ?, ?, ?,
                    ?, ?, ?,
                    'PENDING', 'ACTIVE', NULL, NULL, ?
                )
                ON DUPLICATE KEY UPDATE code = code
                """,
                code,
                visitorName,
                visitorPhone,
                carPlateNo,
                visitCompany,
                visitReason,
                visitTimestamp,
                remark
        );

        if (!sameSubmissionForUpdate(
                code, visitorName, visitorPhone, carPlateNo, visitCompany, visitReason, visitTimestamp, remark
        )) {
            throw new BusinessException(409, "预约提交令牌已用于其他预约，请刷新页面后重试");
        }
        return success(code);
    }

    @PostMapping("/mobile/public/visitors/history")
    public ApiResponse<Map<String, Object>> history(
            @RequestBody(required = false) Map<String, Object> body
    ) {
        List<ReservationCredential> credentials = historyCredentials(body == null ? Map.of() : body);
        if (credentials.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("records", List.of());
            empty.put("total", 0);
            return ApiResponse.success(empty);
        }

        List<String> codes = credentials.stream().map(ReservationCredential::code).toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(codes.size(), "?"));
        List<Map<String, Object>> records = jdbcTemplate.queryForList(
                """
                SELECT code,
                       visitor_name AS visitorName,
                       CONCAT(LEFT(visitor_phone, 3), '****', RIGHT(visitor_phone, 4)) AS visitorPhone,
                       car_plate_no AS carPlateNo,
                       visit_company AS visitCompany,
                       visit_reason AS visitReason,
                       visit_time AS visitAt,
                       CASE
                         WHEN visit_time < NOW() AND appointment_status IN ('PENDING','REGISTERED') THEN 'EXPIRED'
                         ELSE appointment_status
                       END AS status,
                       review_remark AS reviewRemark,
                       CASE
                         WHEN qr_code_file_id IS NOT NULL
                          AND appointment_status IN ('REGISTERED','ENTERED','LEFT')
                         THEN CONCAT('/api/mobile/public/visitors/', code, '/qr')
                         ELSE NULL
                       END AS qrCodeUrl,
                       qr_uploaded_at AS qrUploadedAt,
                       created_at AS createdAt
                FROM visitor_appointment
                WHERE code IN (%s)
                  AND tenant_id IS NULL
                  AND applicant_mobile_user_id IS NULL
                  AND deleted = 0
                ORDER BY created_at DESC, id DESC
                """.formatted(placeholders),
                codes.toArray()
        );
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("records", records);
        response.put("total", records.size());
        return ApiResponse.success(response);
    }

    @GetMapping("/mobile/public/visitors/{code}/qr")
    public ResponseEntity<Resource> qrCode(
            @PathVariable String code,
            @RequestHeader(name = SUBMISSION_TOKEN_HEADER, required = false) String submissionToken
    ) {
        ReservationCredential credential = requireCredential(code, submissionToken);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                """
                SELECT f.storage_path AS storagePath,
                       f.original_name AS originalName,
                       f.mime_type AS mimeType
                FROM visitor_appointment v
                INNER JOIN file_upload f
                        ON f.id = v.qr_code_file_id
                       AND f.biz_type = 'VISITOR'
                       AND f.biz_id = v.id
                       AND f.workspace_key = 'visitors'
                       AND f.access_scope = 'INTERNAL'
                       AND f.status = 'ENABLED'
                       AND f.deleted = 0
                WHERE v.code = ?
                  AND v.tenant_id IS NULL
                  AND v.applicant_mobile_user_id IS NULL
                  AND v.appointment_status IN ('REGISTERED','ENTERED','LEFT')
                  AND v.deleted = 0
                LIMIT 1
                """,
                credential.code()
        );
        if (rows.isEmpty()) {
            throw new BusinessException(404, "门禁二维码尚未上传或预约状态不可用");
        }
        Map<String, Object> file = rows.getFirst();
        Path target = resolveStoragePath(String.valueOf(file.get("storagePath")));
        String mime = file.get("mimeType") == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : String.valueOf(file.get("mimeType"));
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(mime);
        } catch (RuntimeException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(mediaType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(String.valueOf(file.get("originalName")), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(new FileSystemResource(target));
    }

    private List<ReservationCredential> historyCredentials(Map<String, Object> body) {
        if (!body.keySet().equals(Set.of("appointments"))) {
            throw new BusinessException(400, "历史查询只允许提交 appointments");
        }
        Object rawAppointments = body.get("appointments");
        if (!(rawAppointments instanceof List<?> appointments)) {
            throw new BusinessException(400, "appointments 必须为数组");
        }
        if (appointments.size() > HISTORY_LIMIT) {
            throw new BusinessException(400, "单次最多查询" + HISTORY_LIMIT + "条预约");
        }
        List<ReservationCredential> credentials = new ArrayList<>();
        Set<String> seenCodes = new LinkedHashSet<>();
        for (Object item : appointments) {
            if (!(item instanceof Map<?, ?> rawCredential)) {
                throw new BusinessException(400, "预约凭证格式不正确");
            }
            Map<String, Object> credential = new LinkedHashMap<>();
            rawCredential.forEach((key, value) -> credential.put(String.valueOf(key), value));
            if (!credential.keySet().equals(Set.of("code", "submissionToken"))) {
                throw new BusinessException(400, "预约凭证只允许 code 和 submissionToken");
            }
            String code = requiredText(credential, "code", 40, "缺少预约编号").toUpperCase(Locale.ROOT);
            String submissionToken = requiredText(
                    credential, "submissionToken", 128, "缺少预约提交令牌"
            );
            ReservationCredential verified = requireCredential(code, submissionToken);
            if (seenCodes.add(verified.code())) {
                credentials.add(verified);
            }
        }
        return credentials;
    }

    private ReservationCredential requireCredential(String rawCode, String rawSubmissionToken) {
        String code = rawCode == null ? "" : rawCode.strip().toUpperCase(Locale.ROOT);
        String submissionToken = rawSubmissionToken == null ? "" : rawSubmissionToken.strip();
        if (!SUBMISSION_TOKEN.matcher(submissionToken).matches()
                || !appointmentCode(submissionToken).equals(code)) {
            throw new BusinessException(404, "预约记录不存在或凭证无效");
        }
        return new ReservationCredential(code, submissionToken);
    }

    private Path resolveStoragePath(String storagePath) {
        String relative = storagePath == null ? "" : storagePath.replace('\\', '/');
        if (relative.startsWith("/files/")) {
            relative = relative.substring("/files/".length());
        }
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        Path target = uploadRoot.resolve(relative).toAbsolutePath().normalize();
        if (!target.startsWith(uploadRoot) || !Files.isRegularFile(target)) {
            throw new BusinessException(404, "门禁二维码文件不存在");
        }
        return target;
    }

    private void rejectUnknownFields(Map<String, Object> body) {
        for (String key : body.keySet()) {
            if (!ALLOWED_FIELDS.contains(key)) {
                throw new BusinessException(400, "不得提交未开放字段: " + key);
            }
        }
    }

    private String normalizePhone(String rawPhone) {
        String normalized = rawPhone.replaceAll("\\s+", "");
        if (normalized.startsWith("+86")) {
            normalized = normalized.substring(3);
        } else if (normalized.startsWith("0086")) {
            normalized = normalized.substring(4);
        }
        return ContactValidators.requiredPhone(normalized);
    }

    private LocalDateTime visitTime(String value) {
        try {
            LocalDateTime parsed = LocalDateTime.parse(value.replace(' ', 'T')).withNano(0);
            if (!parsed.isAfter(LocalDateTime.now())) {
                throw new BusinessException(400, "来访时间必须晚于当前时间");
            }
            return parsed;
        } catch (DateTimeParseException exception) {
            throw new BusinessException(400, "来访时间格式不正确");
        }
    }

    private String requiredText(
            Map<String, Object> body,
            String key,
            int maximumLength,
            String requiredMessage
    ) {
        String value = optionalText(body, key, maximumLength);
        if (value == null) {
            throw new BusinessException(400, requiredMessage);
        }
        return value;
    }

    private String optionalText(Map<String, Object> body, String key, int maximumLength) {
        Object raw = body.get(key);
        if (raw == null) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty() || "null".equalsIgnoreCase(value)) {
            return null;
        }
        if (value.length() > maximumLength) {
            throw new BusinessException(400, key + "长度不能超过" + maximumLength);
        }
        return value;
    }

    private boolean sameSubmissionForUpdate(
            String code,
            String visitorName,
            String visitorPhone,
            String carPlateNo,
            String visitCompany,
            String visitReason,
            Timestamp visitTime,
            String remark
    ) {
        List<Long> ids = jdbcTemplate.queryForList(
                """
                SELECT id
                FROM visitor_appointment
                WHERE code = ?
                  AND visitor_name = ?
                  AND visitor_phone = ?
                  AND car_plate_no <=> ?
                  AND visit_company <=> ?
                  AND visit_reason = ?
                  AND visit_time = ?
                  AND remark <=> ?
                  AND deleted = 0
                FOR UPDATE
                """,
                Long.class,
                code,
                visitorName,
                visitorPhone,
                carPlateNo,
                visitCompany,
                visitReason,
                visitTime,
                remark
        );
        return !ids.isEmpty();
    }

    private void enforceRateLimit(String clientAddress, String visitorPhone, String submissionToken) {
        String ipHash = sha256(clientAddress == null || clientAddress.isBlank() ? "unknown" : clientAddress);
        String phoneHash = sha256(visitorPhone);
        String submissionTokenHash = sha256(submissionToken);
        incrementAndCheck(
                RedisKeys.rateLimit("token-" + submissionTokenHash, RATE_LIMIT_API),
                SUBMISSION_TOKEN_LIMIT,
                SUBMISSION_TOKEN_WINDOW
        );
        incrementAndCheck(
                RedisKeys.rateLimit("ip-" + ipHash, RATE_LIMIT_API),
                IP_LIMIT,
                IP_WINDOW
        );
        incrementAndCheck(
                RedisKeys.rateLimit("phone-" + phoneHash, RATE_LIMIT_API),
                PHONE_LIMIT,
                PHONE_WINDOW
        );
    }

    private String clientAddress(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String remoteAddress = canonicalIp(request.getRemoteAddr());
        if (remoteAddress == null) {
            return "unknown";
        }
        if (!trustedProxyAddresses.contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor == null || forwardedFor.isBlank()) {
            forwardedFor = request.getHeader("X-Real-IP");
        }
        if (forwardedFor == null || forwardedFor.isBlank() || forwardedFor.length() > 2048) {
            return remoteAddress;
        }

        String currentAddress = remoteAddress;
        String[] chain = forwardedFor.split(",");
        for (int index = chain.length - 1; index >= 0; index--) {
            if (!trustedProxyAddresses.contains(currentAddress)) {
                break;
            }
            String candidate = canonicalIp(chain[index]);
            if (candidate == null) {
                return remoteAddress;
            }
            currentAddress = candidate;
        }
        return currentAddress;
    }

    private static Set<String> parseTrustedProxyAddresses(String configuredAddresses) {
        Set<String> addresses = new LinkedHashSet<>();
        String configured = configuredAddresses == null || configuredAddresses.isBlank()
                ? DEFAULT_TRUSTED_PROXIES
                : configuredAddresses;
        for (String rawAddress : configured.split(",")) {
            String address = canonicalIp(rawAddress);
            if (address != null) {
                addresses.add(address);
            }
        }
        return Set.copyOf(addresses);
    }

    private static String canonicalIp(String rawAddress) {
        if (rawAddress == null) {
            return null;
        }
        String candidate = rawAddress.trim();
        if (candidate.length() >= 2 && candidate.startsWith("\"") && candidate.endsWith("\"")) {
            candidate = candidate.substring(1, candidate.length() - 1).trim();
        }
        if (candidate.startsWith("[")) {
            int closingBracket = candidate.indexOf(']');
            if (closingBracket <= 1) {
                return null;
            }
            candidate = candidate.substring(1, closingBracket);
        } else if (candidate.indexOf('.') >= 0 && candidate.indexOf(':') == candidate.lastIndexOf(':')) {
            int portSeparator = candidate.lastIndexOf(':');
            if (portSeparator > 0 && candidate.substring(portSeparator + 1).matches("\\d+")) {
                candidate = candidate.substring(0, portSeparator);
            }
        }
        if ((!candidate.contains(".") && !candidate.contains(":"))
                || !candidate.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (Exception exception) {
            return null;
        }
    }

    private void incrementAndCheck(String key, int limit, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                throw new BusinessException(503, "预约提交保护服务暂不可用");
            }
            if (count == 1L) {
                Boolean expiring = redisTemplate.expire(key, window);
                if (!Boolean.TRUE.equals(expiring)) {
                    throw new BusinessException(503, "预约提交保护服务暂不可用");
                }
            }
            if (count > limit) {
                throw new BusinessException(429, "提交过于频繁，请稍后再试");
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new BusinessException(503, "预约提交保护服务暂不可用");
        }
    }

    private Map<String, Object> response(String code) {
        return Map.of(
                "code", code,
                "status", "PENDING"
        );
    }

    private ApiResponse<Map<String, Object>> success(String code) {
        return ApiResponse.success(response(code));
    }

    private String appointmentCode(String submissionToken) {
        return "PVR-" + sha256(submissionToken).substring(0, 32).toUpperCase();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record ReservationCredential(String code, String submissionToken) {
    }
}
