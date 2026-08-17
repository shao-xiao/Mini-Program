package com.dehui.property.modules.lease.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record LeaseActionRequest(
        List<@NotNull Long> roomIds,
        LocalDateTime occurredAt,
        @Size(max = 500) String reason,
        @PositiveOrZero Long version
) {
    public static LeaseActionRequest empty() {
        return new LeaseActionRequest(null, null, null, null);
    }
}
