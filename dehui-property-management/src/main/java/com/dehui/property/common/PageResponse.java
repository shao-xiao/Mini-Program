package com.dehui.property.common;

import java.util.List;

public record PageResponse<T>(
        List<T> records,
        long total,
        int page,
        int pageSize
) {
}
