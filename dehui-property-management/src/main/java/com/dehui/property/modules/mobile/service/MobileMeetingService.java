package com.dehui.property.modules.mobile.service;

import com.dehui.property.common.Result;
import com.dehui.property.modules.meeting.dto.MeetingBookingRequest;
import com.dehui.property.modules.meeting.dto.MeetingBookingResponse;
import com.dehui.property.modules.meeting.dto.MeetingRoomResponse;
import com.dehui.property.modules.meeting.entity.MeetingBooking;
import com.dehui.property.modules.meeting.repository.MeetingBookingRepository;
import com.dehui.property.modules.meeting.repository.MeetingRoomRepository;
import com.dehui.property.modules.meeting.service.MeetingService;
import com.dehui.property.modules.mobile.dto.MobileMeetingBookingRequest;
import com.dehui.property.modules.mobile.dto.MobileMeetingHomeResponse;
import com.dehui.property.modules.mobile.dto.MobileUserProfile;
import com.dehui.property.modules.system.entity.SysUser;
import com.dehui.property.modules.system.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MobileMeetingService {

    private final MobileAuthService mobileAuthService;
    private final MeetingService meetingService;
    private final MeetingBookingRepository meetingBookingRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final SysUserRepository sysUserRepository;

    public Result<MobileMeetingHomeResponse> home(String token, LocalDateTime startTime, LocalDateTime endTime) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!isBookableIdentity(profile)) {
            return Result.error(403, "请先绑定内部员工或租户身份后预约会议室");
        }

        List<MeetingRoomResponse> rooms = startTime == null || endTime == null
                ? meetingService.listRooms()
                : meetingService.listAvailableRooms(startTime, endTime);
        return Result.success(new MobileMeetingHomeResponse(profile, rooms, listMyBookings(profile)));
    }

    public Result<MeetingBookingResponse> create(String token, MobileMeetingBookingRequest request) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!isBookableIdentity(profile)) {
            return Result.error(403, "请先绑定内部员工或租户身份后预约会议室");
        }
        if (request.getStartTime() == null || request.getEndTime() == null || !request.getEndTime().isAfter(request.getStartTime())) {
            return Result.error("结束时间必须晚于开始时间");
        }
        if (request.getStartTime().isBefore(LocalDateTime.now())) {
            return Result.error("不能预约过去的时间");
        }

        MeetingBookingRequest bookingRequest = new MeetingBookingRequest();
        bookingRequest.setMeetingRoomId(request.getMeetingRoomId());
        bookingRequest.setStartTime(request.getStartTime());
        bookingRequest.setEndTime(request.getEndTime());
        bookingRequest.setPurpose(request.getPurpose());
        bookingRequest.setDepartmentName(request.getDepartmentName());
        bookingRequest.setContactPhone(request.getContactPhone());

        SysUser currentUser = new SysUser();
        if ("INTERNAL".equals(profile.getUserType())) {
            currentUser = sysUserRepository.findById(profile.getBoundSysUserId()).orElse(null);
            if (currentUser == null) {
                return Result.error("绑定的内部账号不存在");
            }
            bookingRequest.setApplicantType("INTERNAL");
            bookingRequest.setInternalUserId(currentUser.getId());
            bookingRequest.setApplicantName(currentUser.getRealName());
            bookingRequest.setBillingMode("FREE");
        } else {
            bookingRequest.setApplicantType("TENANT");
            bookingRequest.setTenantId(profile.getBoundTenantId());
            bookingRequest.setApplicantName(profile.getNickname() == null || profile.getNickname().isBlank()
                    ? profile.getBoundTenantName()
                    : profile.getNickname());
            bookingRequest.setDepartmentName(profile.getBoundTenantName());
            bookingRequest.setContactPhone(request.getContactPhone() == null || request.getContactPhone().isBlank()
                    ? profile.getPhone()
                    : request.getContactPhone());
            bookingRequest.setBillingMode(request.getBillingMode() == null || request.getBillingMode().isBlank()
                    ? "HOURLY"
                    : request.getBillingMode());
        }

        return meetingService.createBooking(bookingRequest, currentUser);
    }

    public Result<MeetingBookingResponse> cancel(String token, Long bookingId) {
        MobileUserProfile profile = mobileAuthService.getProfile(token);
        if (profile == null) {
            return Result.error(401, "未登录或登录已过期");
        }
        if (!isBookableIdentity(profile)) {
            return Result.error(403, "请先绑定内部员工或租户身份");
        }

        MeetingBooking booking = meetingBookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return Result.error("预约不存在");
        }
        if (!belongsToProfile(booking, profile)) {
            return Result.error(403, "不能取消他人的预约");
        }
        if ("COMPLETED".equals(booking.getStatus())) {
            return Result.error("已完成的预约不能取消");
        }
        if ("CANCELLED".equals(booking.getStatus())) {
            return Result.error("预约已取消");
        }

        return meetingService.cancelBooking(bookingId);
    }

    private boolean isBookableIdentity(MobileUserProfile profile) {
        return ("INTERNAL".equals(profile.getUserType()) && profile.getBoundSysUserId() != null)
                || ("TENANT".equals(profile.getUserType()) && profile.getBoundTenantId() != null);
    }

    private boolean belongsToProfile(MeetingBooking booking, MobileUserProfile profile) {
        if ("INTERNAL".equals(profile.getUserType())) {
            return profile.getBoundSysUserId().equals(booking.getInternalUserId());
        }
        if ("TENANT".equals(profile.getUserType())) {
            return profile.getBoundTenantId().equals(booking.getTenantId());
        }
        return false;
    }

    private List<MeetingBookingResponse> listMyBookings(MobileUserProfile profile) {
        if ("INTERNAL".equals(profile.getUserType())) {
            return meetingBookingRepository.findByInternalUserIdOrderByStartTimeDesc(profile.getBoundSysUserId())
                    .stream()
                    .map(this::toBookingResponse)
                    .toList();
        }
        return meetingBookingRepository.findByTenantIdOrderByStartTimeDesc(profile.getBoundTenantId())
                .stream()
                .map(this::toBookingResponse)
                .toList();
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
