package com.dehui.property.modules.meeting.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class MeetingBookingStatsResponse {
    private long todayBookingCount;
    private long monthBookingCount;
    private BigDecimal monthRevenue;
    private long internalFreeCount;
    private long tenantPaidCount;
    private long cancelledCount;
    private BigDecimal cancelRate;
    private List<TopRoom> topRooms;

    @Data
    @AllArgsConstructor
    public static class TopRoom {
        private Long roomId;
        private String roomName;
        private long bookingCount;
    }
}
