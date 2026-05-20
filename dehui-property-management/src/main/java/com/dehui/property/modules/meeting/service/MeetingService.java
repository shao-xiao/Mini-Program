package com.dehui.property.modules.meeting.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.meeting.dto.InternalApplicantResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingCalculateResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingCancelRequest;
import com.dehui.property.modules.meeting.dto.MeetingBookingLogResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingRequest;
import com.dehui.property.modules.meeting.dto.MeetingBookingResponse;
import com.dehui.property.modules.meeting.dto.MeetingBookingStatsResponse;
import com.dehui.property.modules.meeting.dto.MeetingRoomRequest;
import com.dehui.property.modules.meeting.dto.MeetingRoomResponse;
import com.dehui.property.modules.meeting.entity.MeetingBooking;
import com.dehui.property.modules.meeting.entity.MeetingBookingLog;
import com.dehui.property.modules.meeting.entity.MeetingRoom;
import com.dehui.property.modules.meeting.repository.MeetingBookingLogRepository;
import com.dehui.property.modules.meeting.repository.MeetingBookingRepository;
import com.dehui.property.modules.meeting.repository.MeetingRoomRepository;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.repository.SysUserRepository;
import com.dehui.property.modules.tenant.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingService {
    private static final List<String> CONFLICT_STATUSES = List.of("PENDING", "CONFIRMED");
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter BILL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final LocalTime WORK_START = LocalTime.of(9, 0);
    private static final LocalTime WORK_END = LocalTime.of(18, 0);

    private final MeetingRoomRepository meetingRoomRepository;
    private final MeetingBookingRepository meetingBookingRepository;
    private final MeetingBookingLogRepository meetingBookingLogRepository;
    private final BillRepository billRepository;
    private final SysUserRepository sysUserRepository;
    private final TenantRepository tenantRepository;

    public List<MeetingRoomResponse> listRooms(String name, String status, Integer minCapacity) {
        return meetingRoomRepository.findAll().stream()
                .map(this::normalizeRoomEntity)
                .filter(room -> isBlank(name) || safe(room.getRoomName()).contains(name.trim()))
                .filter(room -> isBlank(status) || status.trim().equals(room.getStatus()))
                .filter(room -> minCapacity == null || (room.getCapacity() != null && room.getCapacity() >= minCapacity))
                .sorted(Comparator.comparing(room -> safe(room.getRoomName())))
                .map(this::toRoomResponse)
                .toList();
    }

    public List<MeetingRoomResponse> listRooms() {
        return listRooms(null, null, null);
    }

    public Result<MeetingRoomResponse> getRoom(Long id) {
        return meetingRoomRepository.findById(id)
                .map(room -> Result.success(toRoomResponse(normalizeRoomEntity(room))))
                .orElseGet(() -> Result.error("会议室不存在"));
    }

    public List<MeetingRoomResponse> listAvailableRooms(LocalDateTime startTime, LocalDateTime endTime) {
        return meetingRoomRepository.findAll().stream()
                .map(this::normalizeRoomEntity)
                .map(room -> {
                    MeetingRoomResponse response = toRoomResponse(room);
                    if (!"AVAILABLE".equals(room.getStatus())) {
                        response.setAvailable(false);
                        response.setUnavailableReason("会议室当前不可预约");
                    } else if (!isValidTime(startTime, endTime)) {
                        response.setAvailable(false);
                        response.setUnavailableReason("请先选择有效时间");
                    } else {
                        boolean conflict = hasConflict(room.getId(), startTime, endTime, null);
                        response.setAvailable(!conflict);
                        response.setUnavailableReason(conflict ? "该时段已被预约" : null);
                    }
                    return response;
                })
                .toList();
    }

    @Transactional
    public MeetingRoomResponse createRoom(MeetingRoomRequest request) {
        validateRoomRequest(request);
        MeetingRoom room = new MeetingRoom();
        applyRoom(room, request);
        return toRoomResponse(meetingRoomRepository.save(room));
    }

    @Transactional
    public MeetingRoomResponse updateRoom(Long id, MeetingRoomRequest request) {
        validateRoomRequest(request);
        MeetingRoom room = meetingRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("会议室不存在"));
        applyRoom(room, request);
        return toRoomResponse(meetingRoomRepository.save(room));
    }

    @Transactional
    public Result<Void> deleteRoom(Long id) {
        return meetingRoomRepository.findById(id)
                .map(room -> {
                    meetingRoomRepository.delete(room);
                    return Result.<Void>success();
                })
                .orElseGet(() -> Result.error("会议室不存在"));
    }

    public List<MeetingBookingResponse> listBookings(String bookingNo, Long roomId, String sourceType, String status,
                                                     String applicantName, String tenantName, String department,
                                                     LocalDate startDate, LocalDate endDate) {
        LocalDateTime startTime = startDate == null ? null : startDate.atStartOfDay();
        LocalDateTime endTime = endDate == null ? null : endDate.plusDays(1).atStartOfDay();
        return meetingBookingRepository.search(blankToNull(bookingNo), roomId, blankToNull(sourceType), blankToNull(normalizeStatus(status)),
                        blankToNull(applicantName), blankToNull(tenantName), blankToNull(department), startTime, endTime)
                .stream()
                .map(this::normalizeBookingEntity)
                .sorted(Comparator.comparing(MeetingBooking::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toBookingResponse)
                .toList();
    }

    public List<MeetingBookingResponse> listBookings() {
        return listBookings(null, null, null, null, null, null, null, null, null);
    }

    public Result<MeetingBookingResponse> getBooking(Long id) {
        return meetingBookingRepository.findById(id)
                .map(booking -> Result.success(toBookingResponse(normalizeBookingEntity(booking))))
                .orElseGet(() -> Result.error("预约不存在"));
    }

    public List<InternalApplicantResponse> listInternalApplicants() {
        return sysUserRepository.findAll().stream()
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .map(user -> new InternalApplicantResponse(user.getId(), user.getUsername(), user.getRealName(), user.getPhone()))
                .toList();
    }

    @Transactional
    public Result<MeetingBookingResponse> createBooking(MeetingBookingRequest request, SysUser currentUser) {
        Result<MeetingBooking> built = buildBooking(new MeetingBooking(), request, currentUser, "CREATE");
        if (built.getCode() != 200) {
            return Result.error(built.getMessage());
        }
        MeetingBooking saved = meetingBookingRepository.save(built.getData());
        writeLog(saved, "CREATE", null, saved.getStatus(), operator(currentUser), "创建预约");
        return Result.success(toBookingResponse(saved));
    }

    @Transactional
    public Result<MeetingBookingResponse> updateBooking(Long id, MeetingBookingRequest request, SysUser currentUser) {
        MeetingBooking booking = meetingBookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return Result.error("预约不存在");
        }
        if ("CANCELLED".equals(normalizeStatus(booking.getStatus())) || "COMPLETED".equals(normalizeStatus(booking.getStatus()))) {
            return Result.error("当前状态不允许修改预约");
        }
        String oldStatus = normalizeStatus(booking.getStatus());
        Result<MeetingBooking> built = buildBooking(booking, request, currentUser, "UPDATE");
        if (built.getCode() != 200) {
            return Result.error(built.getMessage());
        }
        MeetingBooking saved = meetingBookingRepository.save(built.getData());
        writeLog(saved, "UPDATE", oldStatus, saved.getStatus(), operator(currentUser), "修改预约");
        return Result.success(toBookingResponse(saved));
    }

    @Transactional
    public Result<MeetingBookingResponse> confirmBooking(Long id, String operator) {
        MeetingBooking booking = meetingBookingRepository.findById(id).map(this::normalizeBookingEntity).orElse(null);
        if (booking == null) {
            return Result.error("预约不存在");
        }
        if (!"PENDING".equals(booking.getStatus())) {
            return Result.error("只有已预约状态可以确认");
        }
        if (hasConflict(booking.getRoomId(), booking.getStartTime(), booking.getEndTime(), booking.getId())) {
            return Result.error("该会议室在所选时间段已有预约");
        }
        String oldStatus = booking.getStatus();
        booking.setStatus("CONFIRMED");
        booking.setConfirmTime(LocalDateTime.now());
        booking.setUpdatedBy(defaultOperator(operator));

        if ("TENANT".equals(booking.getSourceType()) && amount(booking).compareTo(BigDecimal.ZERO) > 0 && booking.getBillingId() == null) {
            Result<Bill> billResult = createMeetingBill(booking);
            if (billResult.getCode() != 200) {
                return Result.error(billResult.getMessage());
            }
            Bill bill = billResult.getData();
            booking.setBillingId(bill.getId());
            booking.setBillId(bill.getId());
            writeLog(booking, "BILL_GENERATED", oldStatus, booking.getStatus(), defaultOperator(operator), "生成账单ID：" + bill.getId());
        }

        MeetingBooking saved = meetingBookingRepository.save(booking);
        writeLog(saved, "CONFIRM", oldStatus, saved.getStatus(), defaultOperator(operator), "确认预约");
        return Result.success(toBookingResponse(saved));
    }

    @Transactional
    public Result<MeetingBookingResponse> cancelBooking(Long id) {
        return cancelBooking(id, null);
    }

    @Transactional
    public Result<MeetingBookingResponse> cancelBooking(Long id, MeetingBookingCancelRequest request) {
        MeetingBooking booking = meetingBookingRepository.findById(id).map(this::normalizeBookingEntity).orElse(null);
        if (booking == null) {
            return Result.error("预约不存在");
        }
        if ("COMPLETED".equals(booking.getStatus())) {
            return Result.error("已完成的预约不能取消");
        }
        if ("CANCELLED".equals(booking.getStatus())) {
            return Result.error("预约已取消");
        }
        Long billingId = booking.getBillingId();
        if (billingId != null) {
            Bill bill = billRepository.findById(billingId).orElse(null);
            if (bill != null && "PAID".equals(bill.getStatus())) {
                return Result.error("该预约账单已确认收款，不能直接取消，请先走财务退款或冲销流程。");
            }
            if (bill != null) {
                bill.setStatus("CANCELLED");
                bill.setRemark(appendRemark(bill.getRemark(), "会议预约已取消"));
                billRepository.save(bill);
            }
        }
        String oldStatus = booking.getStatus();
        booking.setStatus("CANCELLED");
        booking.setCancelTime(LocalDateTime.now());
        booking.setCancelReason(request == null ? null : request.getCancelReason());
        booking.setUpdatedBy(request == null ? null : request.getOperator());
        MeetingBooking saved = meetingBookingRepository.save(booking);
        writeLog(saved, "CANCEL", oldStatus, saved.getStatus(), request == null ? null : request.getOperator(), booking.getCancelReason());
        return Result.success(toBookingResponse(saved));
    }

    @Transactional
    public Result<MeetingBookingResponse> completeBooking(Long id, String operator) {
        MeetingBooking booking = meetingBookingRepository.findById(id).map(this::normalizeBookingEntity).orElse(null);
        if (booking == null) {
            return Result.error("预约不存在");
        }
        if (!"CONFIRMED".equals(booking.getStatus())) {
            return Result.error("只有已确认预约可以完成");
        }
        String oldStatus = booking.getStatus();
        booking.setStatus("COMPLETED");
        booking.setCompleteTime(LocalDateTime.now());
        booking.setUpdatedBy(defaultOperator(operator));
        MeetingBooking saved = meetingBookingRepository.save(booking);
        writeLog(saved, "COMPLETE", oldStatus, saved.getStatus(), operator, "完成预约");
        return Result.success(toBookingResponse(saved));
    }

    public Result<MeetingBookingCalculateResponse> calculate(Long roomId, String sourceType, LocalDateTime startTime, LocalDateTime endTime) {
        MeetingRoom room = meetingRoomRepository.findById(roomId).map(this::normalizeRoomEntity).orElse(null);
        if (room == null) {
            return Result.error("会议室不存在");
        }
        if (!isValidTime(startTime, endTime)) {
            return Result.error("结束时间必须晚于开始时间");
        }
        String normalizedSource = normalizeSource(sourceType);
        if ("INTERNAL".equals(normalizedSource)) {
            return Result.success(new MeetingBookingCalculateResponse("INTERNAL_FREE", BigDecimal.ZERO, minutes(startTime, endTime), "内部员工免费"));
        }
        BigDecimal total = calculateHourlyAmount(room, startTime, endTime);
        return Result.success(new MeetingBookingCalculateResponse("HOURLY", total, minutes(startTime, endTime), "按会议室费率分钟折算"));
    }

    public Result<List<MeetingBookingLogResponse>> logs(Long bookingId) {
        if (!meetingBookingRepository.existsById(bookingId)) {
            return Result.error("预约不存在");
        }
        return Result.success(meetingBookingLogRepository.findByBookingIdOrderByCreatedTimeDesc(bookingId).stream()
                .map(this::toLogResponse)
                .toList());
    }

    public MeetingBookingStatsResponse stats() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.plusDays(1).atStartOfDay();
        LocalDateTime monthStartTime = monthStart.atStartOfDay();
        LocalDateTime monthEndTime = monthEnd.atStartOfDay();

        List<MeetingBooking> monthBookings = meetingBookingRepository.findByStartTimeBetweenOpen(monthStartTime, monthEndTime).stream()
                .map(this::normalizeBookingEntity)
                .toList();
        long cancelled = monthBookings.stream().filter(item -> "CANCELLED".equals(item.getStatus())).count();
        long total = monthBookings.size();

        MeetingBookingStatsResponse response = new MeetingBookingStatsResponse();
        response.setTodayBookingCount(meetingBookingRepository.countByStartRange(todayStart, todayEnd));
        response.setMonthBookingCount(total);
        response.setMonthRevenue(monthBookings.stream()
                .filter(item -> "TENANT".equals(item.getSourceType()))
                .filter(item -> !"CANCELLED".equals(item.getStatus()))
                .map(this::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        response.setInternalFreeCount(monthBookings.stream().filter(item -> "INTERNAL".equals(item.getSourceType())).count());
        response.setTenantPaidCount(monthBookings.stream().filter(item -> "TENANT".equals(item.getSourceType())).count());
        response.setCancelledCount(cancelled);
        response.setCancelRate(total == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(cancelled * 100.0 / total).setScale(2, RoundingMode.HALF_UP));
        Map<Long, List<MeetingBooking>> byRoom = monthBookings.stream().collect(Collectors.groupingBy(MeetingBooking::getRoomId, LinkedHashMap::new, Collectors.toList()));
        response.setTopRooms(byRoom.entrySet().stream()
                .map(entry -> new MeetingBookingStatsResponse.TopRoom(entry.getKey(), roomName(entry.getKey()), entry.getValue().size()))
                .sorted(Comparator.comparing(MeetingBookingStatsResponse.TopRoom::getBookingCount).reversed())
                .limit(5)
                .toList());
        return response;
    }

    private Result<MeetingBooking> buildBooking(MeetingBooking booking, MeetingBookingRequest request, SysUser currentUser, String action) {
        Long roomId = request.getRoomId() == null ? request.getMeetingRoomId() : request.getRoomId();
        MeetingRoom room = meetingRoomRepository.findById(roomId).map(this::normalizeRoomEntity).orElse(null);
        if (room == null) {
            return Result.error("会议室不存在");
        }
        if (!"AVAILABLE".equals(room.getStatus())) {
            return Result.error("会议室当前不可预约");
        }
        if (!isValidTime(request.getStartTime(), request.getEndTime())) {
            return Result.error("结束时间必须晚于开始时间");
        }
        if (hasConflict(roomId, request.getStartTime(), request.getEndTime(), booking.getId())) {
            return Result.error("该会议室在所选时间段已有预约");
        }

        String sourceType = normalizeSource(firstText(request.getSourceType(), request.getApplicantType()));
        if (!List.of("INTERNAL", "TENANT").contains(sourceType)) {
            return Result.error("来源类型必须是内部员工或租户");
        }
        String status = isBlank(booking.getStatus()) ? "PENDING" : normalizeStatus(booking.getStatus());
        String feeType = "INTERNAL".equals(sourceType) ? "INTERNAL_FREE" : "HOURLY";
        BigDecimal amount = "INTERNAL".equals(sourceType) ? BigDecimal.ZERO : calculateHourlyAmount(room, request.getStartTime(), request.getEndTime());

        booking.setBookingNo(isBlank(booking.getBookingNo()) ? generateBookingNo() : booking.getBookingNo());
        booking.setBookingNumber(booking.getBookingNo());
        booking.setRoomId(roomId);
        booking.setMeetingRoomId(roomId);
        booking.setRoomName(room.getRoomName());
        booking.setSourceType(sourceType);
        booking.setApplicantType(sourceType);
        booking.setTenantId(request.getTenantId());
        booking.setTenantName(resolveTenantName(request));
        booking.setInternalUserId(resolveInternalUserId(request, currentUser, sourceType));
        booking.setApplicantName(resolveApplicantName(request, currentUser, sourceType));
        booking.setDepartment(firstText(request.getDepartment(), request.getDepartmentName()));
        booking.setDepartmentName(booking.getDepartment());
        booking.setApplicantPhone(firstText(request.getApplicantPhone(), request.getContactPhone()));
        booking.setContactPhone(booking.getApplicantPhone());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setRemark(firstText(request.getRemark(), request.getPurpose()));
        booking.setPurpose(booking.getRemark());
        booking.setFeeType(feeType);
        booking.setBillingMode("INTERNAL_FREE".equals(feeType) ? "FREE" : "HOURLY");
        booking.setDiscountRate(BigDecimal.ONE);
        booking.setAmount(amount);
        booking.setCalculatedAmount(amount);
        booking.setStatus(status);
        if ("CREATE".equals(action)) {
            booking.setCreatedBy(operator(currentUser));
        }
        booking.setUpdatedBy(operator(currentUser));
        return Result.success(booking);
    }

    private Result<Bill> createMeetingBill(MeetingBooking booking) {
        if (booking.getTenantId() == null) {
            return Result.error("租户收费预约缺少租户ID，无法生成账单");
        }
        Bill existing = billRepository.findBySourceTypeAndSourceId("MEETING_ROOM", booking.getId()).orElse(null);
        if (existing != null) {
            return Result.success(existing);
        }
        Bill bill = new Bill();
        bill.setBillNumber("BILL-MR-" + LocalDateTime.now().format(NUMBER_TIME) + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        bill.setTenantId(booking.getTenantId());
        bill.setBillType("MEETING_ROOM");
        bill.setTitle("会议室预约费 - " + booking.getRoomName() + " - " + booking.getStartTime().format(BILL_TIME) + "~" + booking.getEndTime().format(BILL_TIME));
        bill.setPeriodStart(booking.getStartTime().toLocalDate());
        bill.setPeriodEnd(booking.getEndTime().toLocalDate());
        bill.setAmount(amount(booking));
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setDueDate(LocalDate.now().plusDays(7));
        bill.setStatus("UNPAID");
        bill.setAuditStatus("PENDING");
        bill.setSourceType("MEETING_ROOM");
        bill.setSourceId(booking.getId());
        bill.setRemark("会议室预约确认后生成");
        return Result.success(billRepository.save(bill));
    }

    private BigDecimal calculateHourlyAmount(MeetingRoom room, LocalDateTime startTime, LocalDateTime endTime) {
        BigDecimal total = BigDecimal.ZERO;
        LocalDateTime cursor = startTime;
        while (cursor.isBefore(endTime)) {
            LocalDateTime next = cursor.plusMinutes(1);
            BigDecimal rate = selectMinuteRate(room, cursor);
            total = total.add(rate.divide(BigDecimal.valueOf(60), 8, RoundingMode.HALF_UP));
            cursor = next;
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal selectMinuteRate(MeetingRoom room, LocalDateTime time) {
        if (isHoliday(time.toLocalDate())) {
            return defaultMoney(room.getWorkdayOffHourRate());
        }
        LocalTime localTime = time.toLocalTime();
        if (!localTime.isBefore(WORK_START) && localTime.isBefore(WORK_END)) {
            return defaultMoney(room.getWorkdayWorkHourRate());
        }
        return defaultMoney(room.getWorkdayOffHourRate());
    }

    private boolean isHoliday(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    private boolean hasConflict(Long roomId, LocalDateTime startTime, LocalDateTime endTime, Long excludeId) {
        if (excludeId == null) {
            return meetingBookingRepository.existsByMeetingRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                    roomId, CONFLICT_STATUSES, endTime, startTime);
        }
        return meetingBookingRepository.existsByMeetingRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThanAndIdNot(
                roomId, CONFLICT_STATUSES, endTime, startTime, excludeId);
    }

    private void applyRoom(MeetingRoom room, MeetingRoomRequest request) {
        room.setRoomName(firstText(request.getName(), request.getRoomName()));
        room.setLocation(request.getLocation());
        room.setCapacity(request.getCapacity());
        room.setFacilities(firstText(request.getEquipment(), request.getFacilities()));
        room.setWorkdayWorkHourRate(defaultMoney(firstMoney(request.getWorkdayHourlyRate(), request.getWorkdayWorkHourRate())));
        room.setWorkdayOffHourRate(defaultMoney(firstMoney(request.getOffHourHourlyRate(), request.getWorkdayOffHourRate())));
        room.setHolidayRate(defaultMoney(firstMoney(request.getHolidayHourlyRate(), request.getHolidayRate())));
        room.setStatus(normalizeRoomStatus(request.getStatus()));
        room.setRemark(request.getRemark());
    }

    private void validateRoomRequest(MeetingRoomRequest request) {
        if (isBlank(firstText(request.getName(), request.getRoomName()))) {
            throw new RuntimeException("会议室名称不能为空");
        }
        if (isBlank(request.getLocation())) {
            throw new RuntimeException("位置不能为空");
        }
        if (request.getCapacity() == null || request.getCapacity() <= 0) {
            throw new RuntimeException("容纳人数必须大于0");
        }
        if (defaultMoney(firstMoney(request.getWorkdayHourlyRate(), request.getWorkdayWorkHourRate())).compareTo(BigDecimal.ZERO) < 0
                || defaultMoney(firstMoney(request.getOffHourHourlyRate(), request.getWorkdayOffHourRate())).compareTo(BigDecimal.ZERO) < 0
                || defaultMoney(firstMoney(request.getHolidayHourlyRate(), request.getHolidayRate())).compareTo(BigDecimal.ZERO) < 0) {
            throw new RuntimeException("费率不能为负数");
        }
    }

    private MeetingRoom normalizeRoomEntity(MeetingRoom room) {
        room.setStatus(normalizeRoomStatus(room.getStatus()));
        return room;
    }

    private MeetingBooking normalizeBookingEntity(MeetingBooking booking) {
        if (isBlank(booking.getBookingNo())) {
            booking.setBookingNo(booking.getBookingNumber());
        }
        if (isBlank(booking.getBookingNumber())) {
            booking.setBookingNumber(booking.getBookingNo());
        }
        if (booking.getRoomId() == null) {
            booking.setRoomId(booking.getMeetingRoomId());
        }
        if (booking.getMeetingRoomId() == null) {
            booking.setMeetingRoomId(booking.getRoomId());
        }
        if (isBlank(booking.getSourceType())) {
            booking.setSourceType(booking.getApplicantType());
        }
        booking.setSourceType(normalizeSource(booking.getSourceType()));
        booking.setApplicantType(booking.getSourceType());
        if (isBlank(booking.getDepartment())) {
            booking.setDepartment(booking.getDepartmentName());
        }
        if (isBlank(booking.getDepartmentName())) {
            booking.setDepartmentName(booking.getDepartment());
        }
        if (isBlank(booking.getApplicantPhone())) {
            booking.setApplicantPhone(booking.getContactPhone());
        }
        if (isBlank(booking.getContactPhone())) {
            booking.setContactPhone(booking.getApplicantPhone());
        }
        if (isBlank(booking.getFeeType())) {
            booking.setFeeType("FREE".equals(booking.getBillingMode()) ? "INTERNAL_FREE" : booking.getBillingMode());
        }
        if (isBlank(booking.getFeeType())) {
            booking.setFeeType("INTERNAL".equals(booking.getSourceType()) ? "INTERNAL_FREE" : "HOURLY");
        }
        booking.setBillingMode("INTERNAL_FREE".equals(booking.getFeeType()) ? "FREE" : booking.getFeeType());
        if (booking.getAmount() == null) {
            booking.setAmount(defaultMoney(booking.getCalculatedAmount()));
        }
        if (booking.getCalculatedAmount() == null) {
            booking.setCalculatedAmount(defaultMoney(booking.getAmount()));
        }
        if (booking.getBillingId() == null) {
            booking.setBillingId(booking.getBillId());
        }
        if (booking.getBillId() == null) {
            booking.setBillId(booking.getBillingId());
        }
        booking.setStatus(normalizeStatus(booking.getStatus()));
        if (isBlank(booking.getRoomName()) && booking.getRoomId() != null) {
            booking.setRoomName(roomName(booking.getRoomId()));
        }
        return booking;
    }

    private MeetingRoomResponse toRoomResponse(MeetingRoom room) {
        MeetingRoomResponse response = new MeetingRoomResponse();
        response.setId(room.getId());
        response.setName(room.getRoomName());
        response.setRoomName(room.getRoomName());
        response.setLocation(room.getLocation());
        response.setCapacity(room.getCapacity());
        response.setEquipment(room.getFacilities());
        response.setFacilities(room.getFacilities());
        response.setWorkdayHourlyRate(defaultMoney(room.getWorkdayWorkHourRate()));
        response.setWorkdayWorkHourRate(defaultMoney(room.getWorkdayWorkHourRate()));
        response.setOffHourHourlyRate(defaultMoney(room.getWorkdayOffHourRate()));
        response.setWorkdayOffHourRate(defaultMoney(room.getWorkdayOffHourRate()));
        response.setHolidayHourlyRate(defaultMoney(room.getHolidayRate()));
        response.setHolidayRate(defaultMoney(room.getHolidayRate()));
        response.setStatus(room.getStatus());
        response.setStatusText(roomStatusText(room.getStatus()));
        response.setRemark(room.getRemark());
        response.setAvailable("AVAILABLE".equals(room.getStatus()));
        response.setUnavailableReason("AVAILABLE".equals(room.getStatus()) ? null : "会议室当前不可预约");
        response.setCreatedTime(room.getCreatedTime());
        response.setUpdatedTime(room.getUpdatedTime());
        return response;
    }

    private MeetingBookingResponse toBookingResponse(MeetingBooking booking) {
        booking = normalizeBookingEntity(booking);
        MeetingBookingResponse response = new MeetingBookingResponse();
        response.setId(booking.getId());
        response.setBookingNo(booking.getBookingNo());
        response.setBookingNumber(booking.getBookingNo());
        response.setRoomId(booking.getRoomId());
        response.setMeetingRoomId(booking.getRoomId());
        response.setRoomName(booking.getRoomName());
        response.setMeetingRoomName(booking.getRoomName());
        response.setSourceType(booking.getSourceType());
        response.setSourceTypeText(sourceTypeText(booking.getSourceType()));
        response.setApplicantType(booking.getSourceType());
        response.setTenantId(booking.getTenantId());
        response.setTenantName(booking.getTenantName());
        response.setInternalUserId(booking.getInternalUserId());
        response.setApplicantName(booking.getApplicantName());
        response.setDepartment(booking.getDepartment());
        response.setDepartmentName(booking.getDepartment());
        response.setApplicantPhone(booking.getApplicantPhone());
        response.setContactPhone(booking.getApplicantPhone());
        response.setStartTime(booking.getStartTime());
        response.setEndTime(booking.getEndTime());
        response.setRemark(booking.getRemark());
        response.setPurpose(booking.getRemark());
        response.setFeeType(booking.getFeeType());
        response.setFeeTypeText(feeTypeText(booking.getFeeType()));
        response.setBillingMode(booking.getBillingMode());
        response.setDiscountRate(booking.getDiscountRate());
        response.setAmount(amount(booking));
        response.setCalculatedAmount(amount(booking));
        response.setBillingId(booking.getBillingId());
        response.setBillId(booking.getBillingId());
        if (booking.getBillingId() != null) {
            billRepository.findById(booking.getBillingId()).ifPresent(bill -> {
                response.setBillStatus(bill.getStatus());
                response.setBillPaid("PAID".equals(bill.getStatus()));
            });
        }
        response.setStatus(booking.getStatus());
        response.setStatusText(statusText(booking.getStatus()));
        response.setConfirmTime(booking.getConfirmTime());
        response.setCancelTime(booking.getCancelTime());
        response.setCompleteTime(booking.getCompleteTime());
        response.setCancelReason(booking.getCancelReason());
        response.setCreatedBy(booking.getCreatedBy());
        response.setUpdatedBy(booking.getUpdatedBy());
        response.setCreatedTime(booking.getCreatedTime());
        response.setUpdatedTime(booking.getUpdatedTime());
        return response;
    }

    private MeetingBookingLogResponse toLogResponse(MeetingBookingLog log) {
        MeetingBookingLogResponse response = new MeetingBookingLogResponse();
        response.setId(log.getId());
        response.setBookingId(log.getBookingId());
        response.setAction(log.getAction());
        response.setActionText(actionText(log.getAction()));
        response.setOldStatus(log.getOldStatus());
        response.setNewStatus(log.getNewStatus());
        response.setOperator(log.getOperator());
        response.setRemark(log.getRemark());
        response.setCreatedTime(log.getCreatedTime());
        return response;
    }

    private void writeLog(MeetingBooking booking, String action, String oldStatus, String newStatus, String operator, String remark) {
        MeetingBookingLog log = new MeetingBookingLog();
        log.setBookingId(booking.getId());
        log.setAction(action);
        log.setOldStatus(oldStatus);
        log.setNewStatus(newStatus);
        log.setOperator(defaultOperator(operator));
        log.setRemark(remark);
        meetingBookingLogRepository.save(log);
    }

    private String resolveTenantName(MeetingBookingRequest request) {
        if (!isBlank(request.getTenantName())) {
            return request.getTenantName();
        }
        if (request.getTenantId() == null) {
            return null;
        }
        return tenantRepository.findById(request.getTenantId()).map(tenant -> tenant.getTenantName()).orElse(null);
    }

    private Long resolveInternalUserId(MeetingBookingRequest request, SysUser currentUser, String sourceType) {
        if (!"INTERNAL".equals(sourceType)) {
            return null;
        }
        if (request.getInternalUserId() != null) {
            return request.getInternalUserId();
        }
        return currentUser == null ? null : currentUser.getId();
    }

    private String resolveApplicantName(MeetingBookingRequest request, SysUser currentUser, String sourceType) {
        if (!isBlank(request.getApplicantName())) {
            return request.getApplicantName();
        }
        if ("INTERNAL".equals(sourceType) && request.getInternalUserId() != null) {
            return sysUserRepository.findById(request.getInternalUserId())
                    .map(user -> firstText(user.getRealName(), user.getUsername()))
                    .orElse(null);
        }
        if ("INTERNAL".equals(sourceType) && currentUser != null) {
            return firstText(currentUser.getRealName(), currentUser.getUsername());
        }
        return request.getTenantName();
    }

    private String roomName(Long roomId) {
        return meetingRoomRepository.findById(roomId).map(MeetingRoom::getRoomName).orElse(null);
    }

    private String generateBookingNo() {
        return "MR" + LocalDateTime.now().format(NUMBER_TIME);
    }

    private boolean isValidTime(LocalDateTime startTime, LocalDateTime endTime) {
        return startTime != null && endTime != null && endTime.isAfter(startTime);
    }

    private long minutes(LocalDateTime startTime, LocalDateTime endTime) {
        return Duration.between(startTime, endTime).toMinutes();
    }

    private BigDecimal amount(MeetingBooking booking) {
        return defaultMoney(firstMoney(booking.getAmount(), booking.getCalculatedAmount()));
    }

    private BigDecimal firstMoney(BigDecimal first, BigDecimal second) {
        return first == null ? second : first;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeRoomStatus(String status) {
        if (isBlank(status) || "ACTIVE".equals(status)) {
            return "AVAILABLE";
        }
        if ("INACTIVE".equals(status)) {
            return "DISABLED";
        }
        return status;
    }

    private String normalizeStatus(String status) {
        if ("BOOKED".equals(status) || isBlank(status)) {
            return "PENDING";
        }
        return status;
    }

    private String normalizeSource(String sourceType) {
        if (isBlank(sourceType)) {
            return null;
        }
        if ("EXTERNAL".equals(sourceType)) {
            return "TENANT";
        }
        return sourceType;
    }

    private String appendRemark(String current, String addition) {
        return isBlank(current) ? addition : current + "；" + addition;
    }

    private String operator(SysUser currentUser) {
        return currentUser == null ? "system" : firstText(currentUser.getRealName(), currentUser.getUsername());
    }

    private String defaultOperator(String operator) {
        return isBlank(operator) ? "system" : operator;
    }

    private String firstText(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String roomStatusText(String status) {
        return switch (status == null ? "" : status) {
            case "AVAILABLE" -> "可预约";
            case "MAINTENANCE" -> "维护中";
            case "DISABLED" -> "停用";
            default -> status;
        };
    }

    private String statusText(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "已预约";
            case "CONFIRMED" -> "已确认";
            case "CANCELLED" -> "已取消";
            case "COMPLETED" -> "已完成";
            default -> status;
        };
    }

    private String sourceTypeText(String sourceType) {
        return "INTERNAL".equals(sourceType) ? "内部员工" : "租户";
    }

    private String feeTypeText(String feeType) {
        return "INTERNAL_FREE".equals(feeType) ? "内部免费" : "按小时收费";
    }

    private String actionText(String action) {
        return switch (action == null ? "" : action) {
            case "CREATE" -> "创建预约";
            case "UPDATE" -> "修改预约";
            case "CONFIRM" -> "确认预约";
            case "CANCEL" -> "取消预约";
            case "COMPLETE" -> "完成预约";
            case "BILL_GENERATED" -> "生成账单";
            default -> action;
        };
    }
}
