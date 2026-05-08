package com.dehui.property.modules.meeting.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.bill.entity.Bill;
import com.dehui.property.modules.bill.repository.BillRepository;
import com.dehui.property.modules.meeting.dto.MeetingBookingRequest;
import com.dehui.property.modules.meeting.dto.MeetingBookingResponse;
import com.dehui.property.modules.meeting.dto.InternalApplicantResponse;
import com.dehui.property.modules.meeting.dto.MeetingRoomRequest;
import com.dehui.property.modules.meeting.dto.MeetingRoomResponse;
import com.dehui.property.modules.meeting.entity.MeetingBooking;
import com.dehui.property.modules.meeting.entity.MeetingRoom;
import com.dehui.property.modules.meeting.repository.MeetingBookingRepository;
import com.dehui.property.modules.meeting.repository.MeetingRoomRepository;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRoomRepository meetingRoomRepository;
    private final MeetingBookingRepository meetingBookingRepository;
    private final BillRepository billRepository;
    private final SysUserRepository sysUserRepository;

    public List<MeetingRoomResponse> listRooms() {
        return meetingRoomRepository.findAll().stream().map(this::toRoomResponse).toList();
    }

    public List<MeetingRoomResponse> listAvailableRooms(LocalDateTime startTime, LocalDateTime endTime) {
        return meetingRoomRepository.findAll().stream()
                .map(room -> {
                    MeetingRoomResponse response = toRoomResponse(room);
                    if (!"ACTIVE".equals(room.getStatus())) {
                        response.setAvailable(false);
                        response.setUnavailableReason("会议室已停用");
                    } else if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
                        response.setAvailable(false);
                        response.setUnavailableReason("请先选择有效时间");
                    } else {
                        boolean conflict = meetingBookingRepository.existsByMeetingRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                                room.getId(),
                                List.of("BOOKED", "CONFIRMED"),
                                endTime,
                                startTime
                        );
                        response.setAvailable(!conflict);
                        response.setUnavailableReason(conflict ? "该时段已被预约" : null);
                    }
                    return response;
                })
                .toList();
    }

    public MeetingRoomResponse createRoom(MeetingRoomRequest request) {
        MeetingRoom room = new MeetingRoom();
        applyRoom(room, request);
        return toRoomResponse(meetingRoomRepository.save(room));
    }

    public MeetingRoomResponse updateRoom(Long id, MeetingRoomRequest request) {
        MeetingRoom room = meetingRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("会议室不存在"));
        applyRoom(room, request);
        return toRoomResponse(meetingRoomRepository.save(room));
    }

    public void deleteRoom(Long id) {
        meetingRoomRepository.deleteById(id);
    }

    public List<MeetingBookingResponse> listBookings() {
        return meetingBookingRepository.findAll().stream()
                .sorted((a, b) -> b.getStartTime().compareTo(a.getStartTime()))
                .map(this::toBookingResponse)
                .toList();
    }

    public List<InternalApplicantResponse> listInternalApplicants() {
        return sysUserRepository.findAll().stream()
                .filter(user -> "ACTIVE".equals(user.getStatus()))
                .map(user -> new InternalApplicantResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getRealName(),
                        user.getPhone()
                ))
                .toList();
    }

    @Transactional
    public Result<MeetingBookingResponse> createBooking(MeetingBookingRequest request, SysUser currentUser) {
        MeetingRoom room = meetingRoomRepository.findById(request.getMeetingRoomId())
                .orElse(null);
        if (room == null) {
            return Result.error("会议室不存在");
        }
        if (!"ACTIVE".equals(room.getStatus())) {
            return Result.error("会议室已停用，不能预约");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            return Result.error("结束时间必须晚于开始时间");
        }

        boolean conflict = meetingBookingRepository.existsByMeetingRoomIdAndStatusInAndStartTimeLessThanAndEndTimeGreaterThan(
                request.getMeetingRoomId(),
                List.of("BOOKED", "CONFIRMED"),
                request.getEndTime(),
                request.getStartTime()
        );
        if (conflict) {
            return Result.error("该会议室在所选时间段已被预约");
        }

        MeetingBooking booking = new MeetingBooking();
        booking.setBookingNumber(generateBookingNumber());
        booking.setMeetingRoomId(request.getMeetingRoomId());
        booking.setApplicantType(request.getApplicantType());
        booking.setTenantId(request.getTenantId());
        booking.setStartTime(request.getStartTime());
        booking.setEndTime(request.getEndTime());
        booking.setPurpose(request.getPurpose());
        booking.setBillingMode(normalizeBillingMode(request, currentUser));
        booking.setDiscountRate(normalizeDiscountRate(request.getDiscountRate()));
        booking.setStatus("BOOKED");

        if ("INTERNAL".equals(request.getApplicantType())) {
            SysUser applicant = sysUserRepository.findById(request.getInternalUserId() == null
                            ? currentUser.getId()
                            : request.getInternalUserId())
                    .orElse(currentUser);
            booking.setInternalUserId(applicant.getId());
            booking.setApplicantName(applicant.getRealName() == null || applicant.getRealName().isBlank()
                    ? applicant.getUsername()
                    : applicant.getRealName());
            booking.setDepartmentName(request.getDepartmentName());
        } else {
            booking.setApplicantName(request.getApplicantName());
            booking.setDepartmentName(request.getDepartmentName());
            booking.setContactPhone(request.getContactPhone());
        }

        booking.setCalculatedAmount(calculateAmount(room, booking));
        MeetingBooking saved = meetingBookingRepository.save(booking);
        return Result.success(toBookingResponse(saved));
    }

    @Transactional
    public Result<MeetingBookingResponse> confirmBooking(Long id) {
        MeetingBooking booking = meetingBookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return Result.error("预约不存在");
        }
        if ("CANCELLED".equals(booking.getStatus())) {
            return Result.error("已取消的预约不能确认");
        }
        if (!"CONFIRMED".equals(booking.getStatus())) {
            booking.setStatus("CONFIRMED");
        }

        if (booking.getCalculatedAmount() != null
                && booking.getCalculatedAmount().compareTo(BigDecimal.ZERO) > 0
                && booking.getBillId() == null) {
            Bill bill = new Bill();
            bill.setBillNumber(generateBillNumber());
            bill.setTenantId(booking.getTenantId());
            bill.setContractId(null);
            bill.setBillType("MEETING_ROOM");
            bill.setPeriodStart(booking.getStartTime().toLocalDate());
            bill.setPeriodEnd(booking.getEndTime().toLocalDate());
            bill.setAmount(booking.getCalculatedAmount());
            bill.setPaidAmount(BigDecimal.ZERO);
            bill.setDueDate(LocalDate.now().plusDays(7));
            bill.setStatus("UNPAID");
            Bill savedBill = billRepository.save(bill);
            booking.setBillId(savedBill.getId());
        }

        return Result.success(toBookingResponse(meetingBookingRepository.save(booking)));
    }

    @Transactional
    public Result<MeetingBookingResponse> cancelBooking(Long id) {
        MeetingBooking booking = meetingBookingRepository.findById(id).orElse(null);
        if (booking == null) {
            return Result.error("预约不存在");
        }
        if ("COMPLETED".equals(booking.getStatus())) {
            return Result.error("已完成的预约不能取消");
        }
        booking.setStatus("CANCELLED");
        return Result.success(toBookingResponse(meetingBookingRepository.save(booking)));
    }

    private void applyRoom(MeetingRoom room, MeetingRoomRequest request) {
        room.setRoomName(request.getRoomName());
        room.setLocation(request.getLocation());
        room.setCapacity(request.getCapacity());
        room.setFacilities(request.getFacilities());
        room.setWorkdayWorkHourRate(defaultMoney(request.getWorkdayWorkHourRate()));
        room.setWorkdayOffHourRate(defaultMoney(request.getWorkdayOffHourRate()));
        room.setHolidayRate(defaultMoney(request.getHolidayRate()));
        room.setStatus(request.getStatus() == null || request.getStatus().isBlank() ? "ACTIVE" : request.getStatus());
    }

    private BigDecimal calculateAmount(MeetingRoom room, MeetingBooking booking) {
        if ("FREE".equals(booking.getBillingMode()) || "GIFTED".equals(booking.getBillingMode())) {
            return BigDecimal.ZERO;
        }

        BigDecimal rate = selectRate(room, booking.getStartTime(), booking.getEndTime());
        BigDecimal hours = BigDecimal.valueOf(Duration.between(booking.getStartTime(), booking.getEndTime()).toMinutes())
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        if (hours.compareTo(BigDecimal.ONE) < 0) {
            hours = BigDecimal.ONE;
        }

        BigDecimal discount = booking.getDiscountRate() == null ? BigDecimal.ONE : booking.getDiscountRate();
        return rate.multiply(hours).multiply(discount).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal selectRate(MeetingRoom room, LocalDateTime start, LocalDateTime end) {
        DayOfWeek day = start.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return defaultMoney(room.getHolidayRate());
        }
        if (start.toLocalTime().getHour() >= 9 && end.toLocalTime().getHour() <= 18) {
            return defaultMoney(room.getWorkdayWorkHourRate());
        }
        return defaultMoney(room.getWorkdayOffHourRate());
    }

    private String normalizeBillingMode(MeetingBookingRequest request, SysUser currentUser) {
        if ("INTERNAL".equals(request.getApplicantType())) {
            return "FREE";
        }
        if (request.getBillingMode() == null || request.getBillingMode().isBlank()) {
            return "HOURLY";
        }
        return request.getBillingMode();
    }

    private BigDecimal normalizeDiscountRate(BigDecimal discountRate) {
        if (discountRate == null) {
            return BigDecimal.ONE;
        }
        if (discountRate.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (discountRate.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return discountRate;
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String generateBookingNumber() {
        return "MR" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private String generateBillNumber() {
        return "BILL-MR-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    private MeetingRoomResponse toRoomResponse(MeetingRoom room) {
        MeetingRoomResponse response = new MeetingRoomResponse();
        response.setId(room.getId());
        response.setRoomName(room.getRoomName());
        response.setLocation(room.getLocation());
        response.setCapacity(room.getCapacity());
        response.setFacilities(room.getFacilities());
        response.setWorkdayWorkHourRate(room.getWorkdayWorkHourRate());
        response.setWorkdayOffHourRate(room.getWorkdayOffHourRate());
        response.setHolidayRate(room.getHolidayRate());
        response.setStatus(room.getStatus());
        response.setAvailable("ACTIVE".equals(room.getStatus()));
        response.setUnavailableReason("ACTIVE".equals(room.getStatus()) ? null : "会议室已停用");
        response.setCreatedTime(room.getCreatedTime());
        response.setUpdatedTime(room.getUpdatedTime());
        return response;
    }

    private MeetingBookingResponse toBookingResponse(MeetingBooking booking) {
        MeetingBookingResponse response = new MeetingBookingResponse();
        response.setId(booking.getId());
        response.setBookingNumber(booking.getBookingNumber());
        response.setMeetingRoomId(booking.getMeetingRoomId());
        meetingRoomRepository.findById(booking.getMeetingRoomId())
                .ifPresent(room -> response.setMeetingRoomName(room.getRoomName()));
        response.setApplicantType(booking.getApplicantType());
        response.setTenantId(booking.getTenantId());
        response.setInternalUserId(booking.getInternalUserId());
        response.setApplicantName(booking.getApplicantName());
        response.setDepartmentName(booking.getDepartmentName());
        response.setContactPhone(booking.getContactPhone());
        response.setStartTime(booking.getStartTime());
        response.setEndTime(booking.getEndTime());
        response.setPurpose(booking.getPurpose());
        response.setBillingMode(booking.getBillingMode());
        response.setDiscountRate(booking.getDiscountRate());
        response.setCalculatedAmount(booking.getCalculatedAmount());
        response.setBillId(booking.getBillId());
        response.setStatus(booking.getStatus());
        response.setCreatedTime(booking.getCreatedTime());
        response.setUpdatedTime(booking.getUpdatedTime());
        return response;
    }
}
