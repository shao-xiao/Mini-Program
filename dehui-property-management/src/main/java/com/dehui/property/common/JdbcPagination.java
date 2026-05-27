package com.dehui.property.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class JdbcPagination {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private JdbcPagination() {
    }

    public static PageResponse<Map<String, Object>> query(
            JdbcTemplate jdbcTemplate,
            String recordSql,
            String countSql,
            List<Object> args,
            Integer page,
            Integer pageSize
    ) {
        return query(jdbcTemplate, recordSql, countSql, args, page, pageSize, new ColumnMapRowMapper());
    }

    public static <T> PageResponse<T> query(
            JdbcTemplate jdbcTemplate,
            String recordSql,
            String countSql,
            List<Object> args,
            Integer page,
            Integer pageSize,
            RowMapper<T> rowMapper
    ) {
        List<Object> safeArgs = args == null ? List.of() : args;
        int normalizedPage = normalizePage(page);
        int normalizedPageSize = normalizePageSize(pageSize);
        int offset = (normalizedPage - 1) * normalizedPageSize;

        Long total = jdbcTemplate.queryForObject(countSql, Long.class, safeArgs.toArray());

        List<Object> recordArgs = new ArrayList<>(safeArgs);
        recordArgs.add(normalizedPageSize);
        recordArgs.add(offset);
        List<T> records = jdbcTemplate.query(recordSql.stripTrailing() + " LIMIT ? OFFSET ?", rowMapper, recordArgs.toArray());

        return new PageResponse<>(
                records,
                total == null ? 0L : total,
                normalizedPage,
                normalizedPageSize
        );
    }

    private static int normalizePage(Integer page) {
        if (page == null || page < DEFAULT_PAGE) {
            return DEFAULT_PAGE;
        }
        return page;
    }

    private static int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
