package com.dehui.property.modules.mobile.dto;

import com.dehui.property.modules.meeting.dto.MeetingBookingResponse;
import com.dehui.property.modules.meeting.dto.MeetingRoomResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MobileMeetingHomeResponse {
    private MobileUserProfile profile;
    private List<MeetingRoomResponse> rooms;
    private List<MeetingBookingResponse> bookings;
}
