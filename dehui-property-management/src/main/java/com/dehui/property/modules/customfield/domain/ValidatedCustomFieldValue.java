package com.dehui.property.modules.customfield.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record ValidatedCustomFieldValue(
        Object jsonValue,
        String textValue,
        String longTextValue,
        BigDecimal numberValue,
        BigDecimal decimalValue,
        LocalDate dateValue,
        LocalDateTime datetimeValue,
        Boolean booleanValue
) {
    public static ValidatedCustomFieldValue empty() {
        return new ValidatedCustomFieldValue(null, null, null, null, null, null, null, null);
    }
}
