package com.dehui.property.modules.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ContractCreateRequest(
        @Size(max = 64) String contractNo,
        @NotNull Long tenantId,
        @NotEmpty List<@NotNull Long> roomIds,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LocalDate effectiveDate,
        LocalDateTime signedAt,
        @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @DecimalMin("0.00") BigDecimal rentAmount,
        @DecimalMin("0.00") BigDecimal depositAmount,
        Long renewalOfContractId,
        @Size(max = 500) String remark
) {
}
