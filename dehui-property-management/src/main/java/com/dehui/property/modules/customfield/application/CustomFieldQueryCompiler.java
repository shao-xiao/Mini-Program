package com.dehui.property.modules.customfield.application;

import com.dehui.property.common.BusinessException;
import com.dehui.property.modules.customfield.domain.CustomFieldType;
import com.dehui.property.modules.customfield.infrastructure.CustomFieldRepository.DefinitionRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CustomFieldQueryCompiler {

    private static final int MAX_FILTERS = 20;
    private static final int MAX_ORDERINGS = 8;
    private static final Pattern TRUSTED_ENTITY_EXPRESSION =
            Pattern.compile("^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$");
    private static final Set<CustomFieldType> TEXT_TYPES = Set.of(
            CustomFieldType.TEXT,
            CustomFieldType.LONG_TEXT,
            CustomFieldType.PHONE,
            CustomFieldType.EMAIL,
            CustomFieldType.URL
    );
    private static final Set<String> VALUELESS_OPERATORS = Set.of(
            "empty", "notEmpty", "today", "thisWeek", "thisMonth"
    );

    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CustomFieldQueryCompiler(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemDefaultZone());
    }

    public CustomFieldQueryCompiler(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public CompiledQuery compile(
            String entityIdExpression,
            String entityType,
            Collection<DefinitionRecord> definitions,
            String customFilters,
            String groupBy,
            String sorts
    ) {
        return compile(entityIdExpression, entityType, definitions, customFilters, groupBy, sorts, null);
    }

    public CompiledQuery compile(
            String entityIdExpression,
            String entityType,
            Collection<DefinitionRecord> definitions,
            String customFilters,
            String groupBy,
            String sorts,
            NativeOrderPolicy nativeOrderPolicy
    ) {
        requireTrustedEntityExpression(entityIdExpression);
        Map<String, DefinitionRecord> fields = definitionsByCode(definitions);
        List<FilterCondition> conditions = parseFilters(customFilters);
        List<String> predicates = new ArrayList<>();
        List<Object> predicateArguments = new ArrayList<>();

        for (int index = 0; index < conditions.size(); index++) {
            FilterCondition condition = conditions.get(index);
            DefinitionRecord definition = requireDefinition(fields, condition.field());
            PredicateFragment fragment = compilePredicate(
                    "cfv_filter_" + index,
                    entityIdExpression,
                    entityType,
                    definition,
                    condition
            );
            predicates.add(fragment.sql());
            predicateArguments.addAll(fragment.arguments());
        }

        LinkedHashMap<String, OrderFragment> orderings = new LinkedHashMap<>();
        appendOrderings(orderings, fields, groupBy, true, entityIdExpression, entityType, nativeOrderPolicy);
        appendOrderings(orderings, fields, sorts, false, entityIdExpression, entityType, nativeOrderPolicy);
        appendFallbackOrdering(orderings, nativeOrderPolicy);

        List<String> orderExpressions = orderings.values().stream().map(OrderFragment::sql).toList();
        List<Object> orderArguments = orderings.values().stream()
                .flatMap(ordering -> ordering.arguments().stream())
                .toList();
        return new CompiledQuery(
                String.join(" AND ", predicates),
                List.copyOf(predicateArguments),
                List.copyOf(orderExpressions),
                List.copyOf(orderArguments),
                conditions.size()
        );
    }

    private PredicateFragment compilePredicate(
            String alias,
            String entityIdExpression,
            String entityType,
            DefinitionRecord definition,
            FilterCondition condition
    ) {
        String operator = condition.operator();
        requireSupportedOperator(definition.fieldType(), operator);
        String column = alias + "." + definition.fieldType().storageColumn();
        String base = "SELECT 1 FROM custom_field_value " + alias
                + " WHERE " + alias + ".field_id = ?"
                + " AND " + alias + ".entity_type = ?"
                + " AND " + alias + ".entity_id = " + entityIdExpression
                + " AND " + alias + ".deleted = 0";
        List<Object> baseArguments = new ArrayList<>(List.of(definition.id(), entityType));

        if ("empty".equals(operator)) {
            String present = presentExpression(column, definition.fieldType());
            return new PredicateFragment("NOT EXISTS (" + base + " AND " + present + ")", baseArguments);
        }
        if ("notEmpty".equals(operator)) {
            String present = presentExpression(column, definition.fieldType());
            return new PredicateFragment("EXISTS (" + base + " AND " + present + ")", baseArguments);
        }

        Object value = condition.value();
        if (!VALUELESS_OPERATORS.contains(operator) && isMissingValue(value)) {
            throw badRequest("Custom-field filter value is required");
        }

        if (TEXT_TYPES.contains(definition.fieldType())) {
            return textPredicate(base, baseArguments, column, operator, value);
        }
        return switch (definition.fieldType()) {
            case NUMBER, CURRENCY -> comparablePredicate(base, baseArguments, column, operator, value);
            case DATE -> datePredicate(base, baseArguments, column, operator, value, false);
            case DATETIME -> datePredicate(base, baseArguments, column, operator, value, true);
            case SINGLE_SELECT -> selectPredicate(base, baseArguments, column, definition, operator, value);
            case MULTI_SELECT -> multiSelectPredicate(base, baseArguments, column, definition, operator, value);
            case BOOLEAN -> booleanPredicate(base, baseArguments, column, operator, value);
            case ATTACHMENT -> attachmentPredicate(base, baseArguments, column, operator);
            default -> throw badRequest("Unsupported custom-field filter type");
        };
    }

    private PredicateFragment textPredicate(
            String base,
            List<Object> baseArguments,
            String column,
            String operator,
            Object rawValue
    ) {
        String value = String.valueOf(rawValue);
        return switch (operator) {
            case "contains" -> exists(base, baseArguments, "LOCATE(LOWER(?), LOWER(" + column + ")) > 0", value);
            case "notContains" -> notExists(base, baseArguments, "LOCATE(LOWER(?), LOWER(" + column + ")) > 0", value);
            case "equals" -> exists(base, baseArguments, column + " = ?", value);
            case "notEquals" -> notExists(base, baseArguments, column + " = ?", value);
            default -> throw badRequest("Unsupported text custom-field operator: " + operator);
        };
    }

    private PredicateFragment comparablePredicate(
            String base,
            List<Object> baseArguments,
            String column,
            String operator,
            Object rawValue
    ) {
        if ("between".equals(operator)) {
            List<?> range = requireRange(rawValue);
            Object start = comparableValue(range.get(0));
            Object end = comparableValue(range.get(1));
            return exists(base, baseArguments, column + " BETWEEN ? AND ?", start, end);
        }
        Object value = comparableValue(rawValue);
        return switch (operator) {
            case "equals" -> exists(base, baseArguments, column + " = ?", value);
            case "notEquals" -> notExists(base, baseArguments, column + " = ?", value);
            case "greaterThan" -> exists(base, baseArguments, column + " > ?", value);
            case "greaterThanOrEqual" -> exists(base, baseArguments, column + " >= ?", value);
            case "lessThan" -> exists(base, baseArguments, column + " < ?", value);
            case "lessThanOrEqual" -> exists(base, baseArguments, column + " <= ?", value);
            default -> throw badRequest("Unsupported numeric custom-field operator: " + operator);
        };
    }

    private PredicateFragment datePredicate(
            String base,
            List<Object> baseArguments,
            String column,
            String operator,
            Object rawValue,
            boolean datetime
    ) {
        if ("dateRange".equals(operator) || "between".equals(operator)) {
            List<?> range = requireRange(rawValue);
            return exists(base, baseArguments, column + " BETWEEN ? AND ?",
                    temporalValue(range.get(0), datetime), temporalValue(range.get(1), datetime));
        }
        if ("today".equals(operator) || "thisWeek".equals(operator) || "thisMonth".equals(operator)) {
            LocalDate today = LocalDate.now(clock);
            LocalDate start = switch (operator) {
                case "thisWeek" -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                case "thisMonth" -> YearMonth.from(today).atDay(1);
                default -> today;
            };
            LocalDate end = switch (operator) {
                case "thisWeek" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                case "thisMonth" -> YearMonth.from(today).atEndOfMonth();
                default -> today;
            };
            Object startValue = datetime ? start.atStartOfDay() : start;
            Object endValue = datetime ? end.plusDays(1).atStartOfDay().minusNanos(1) : end;
            return exists(base, baseArguments, column + " BETWEEN ? AND ?", startValue, endValue);
        }
        Object value = temporalValue(rawValue, datetime);
        return switch (operator) {
            case "equals" -> exists(base, baseArguments, column + " = ?", value);
            case "notEquals" -> notExists(base, baseArguments, column + " = ?", value);
            case "before" -> exists(base, baseArguments, column + " < ?", value);
            case "after" -> exists(base, baseArguments, column + " > ?", value);
            default -> throw badRequest("Unsupported date custom-field operator: " + operator);
        };
    }

    private PredicateFragment selectPredicate(
            String base,
            List<Object> baseArguments,
            String column,
            DefinitionRecord definition,
            String operator,
            Object rawValue
    ) {
        List<String> values = stringValues(rawValue);
        requireActiveOptions(definition, values);
        String placeholders = String.join(",", values.stream().map(ignored -> "?").toList());
        return switch (operator) {
            case "equals", "containsAny" -> exists(base, baseArguments, column + " IN (" + placeholders + ")", values.toArray());
            case "notEquals" -> notExists(base, baseArguments, column + " = ?", String.valueOf(rawValue));
            case "notContains" -> notExists(base, baseArguments, column + " IN (" + placeholders + ")", values.toArray());
            default -> throw badRequest("Unsupported select custom-field operator: " + operator);
        };
    }

    private PredicateFragment multiSelectPredicate(
            String base,
            List<Object> baseArguments,
            String column,
            DefinitionRecord definition,
            String operator,
            Object rawValue
    ) {
        List<String> values = stringValues(rawValue);
        requireActiveOptions(definition, values);
        if (values.isEmpty()) throw badRequest("Custom-field multi-select value is required");
        String matchesAny = String.join(" OR ", values.stream()
                .map(ignored -> "JSON_CONTAINS(" + column + ", JSON_QUOTE(?), '$')")
                .toList());
        String matchesAll = String.join(" AND ", values.stream()
                .map(ignored -> "JSON_CONTAINS(" + column + ", JSON_QUOTE(?), '$')")
                .toList());
        return switch (operator) {
            case "containsAny", "equals" -> exists(base, baseArguments, "(" + matchesAny + ")", values.toArray());
            case "containsAll" -> exists(base, baseArguments, "(" + matchesAll + ")", values.toArray());
            case "notContains", "notEquals" -> notExists(base, baseArguments, "(" + matchesAny + ")", values.toArray());
            default -> throw badRequest("Unsupported multi-select custom-field operator: " + operator);
        };
    }

    private PredicateFragment booleanPredicate(
            String base,
            List<Object> baseArguments,
            String column,
            String operator,
            Object rawValue
    ) {
        boolean value = booleanValue(rawValue);
        return switch (operator) {
            case "equals" -> exists(base, baseArguments, column + " = ?", value);
            case "notEquals" -> notExists(base, baseArguments, column + " = ?", value);
            default -> throw badRequest("Unsupported boolean custom-field operator: " + operator);
        };
    }

    private PredicateFragment attachmentPredicate(
            String base,
            List<Object> baseArguments,
            String column,
            String operator
    ) {
        String present = presentExpression(column, CustomFieldType.ATTACHMENT);
        return switch (operator) {
            case "empty" -> new PredicateFragment("NOT EXISTS (" + base + " AND " + present + ")", baseArguments);
            case "notEmpty" -> new PredicateFragment("EXISTS (" + base + " AND " + present + ")", baseArguments);
            default -> throw badRequest("Unsupported attachment custom-field operator: " + operator);
        };
    }

    private void appendOrderings(
            LinkedHashMap<String, OrderFragment> orderings,
            Map<String, DefinitionRecord> fields,
            String serialized,
            boolean grouping,
            String entityIdExpression,
            String entityType,
            NativeOrderPolicy nativeOrderPolicy
    ) {
        if (serialized == null || serialized.isBlank()) return;
        for (String token : serialized.split(",")) {
            if (orderings.size() >= MAX_ORDERINGS) return;
            String[] parts = token.trim().split(":", 2);
            if (parts.length != 2) continue;
            if (!isCustomReference(parts[0])) {
                appendNativeOrdering(orderings, nativeOrderPolicy, parts[0], parts[1]);
                continue;
            }
            DefinitionRecord definition = requireDefinition(fields, parts[0]);
            if (definition.fieldType() == CustomFieldType.ATTACHMENT) {
                continue;
            }
            if (grouping && !definition.groupable()) {
                throw badRequest("Custom field does not support grouping: " + definition.fieldCode());
            }
            if (!grouping && !definition.sortable()) {
                throw badRequest("Custom field does not support sorting: " + definition.fieldCode());
            }
            String direction = direction(parts[1]);
            String alias = "cfv_order_" + orderings.size();
            String expression = "(SELECT " + alias + "." + definition.fieldType().storageColumn()
                    + " FROM custom_field_value " + alias
                    + " WHERE " + alias + ".field_id = ?"
                    + " AND " + alias + ".entity_type = ?"
                    + " AND " + alias + ".entity_id = " + entityIdExpression
                    + " AND " + alias + ".deleted = 0 LIMIT 1)";
            orderings.putIfAbsent("custom:" + definition.id(), new OrderFragment(
                    expression + " " + direction,
                    List.of(definition.id(), entityType)
            ));
        }
    }

    private void appendNativeOrdering(
            LinkedHashMap<String, OrderFragment> orderings,
            NativeOrderPolicy policy,
            String field,
            String rawDirection
    ) {
        if (policy == null) return;
        String expression = policy.allowedFields().get(field.trim());
        if (expression == null) return;
        String direction = direction(rawDirection);
        orderings.putIfAbsent("native:" + expression, new OrderFragment(
                expression + " " + direction,
                List.of()
        ));
    }

    private void appendFallbackOrdering(
            LinkedHashMap<String, OrderFragment> orderings,
            NativeOrderPolicy policy
    ) {
        if (policy == null) return;
        if (orderings.isEmpty()) {
            String expression = policy.legacySortField() == null
                    ? null
                    : policy.allowedFields().get(policy.legacySortField());
            if (expression == null) expression = policy.defaultExpression();
            String direction = policy.legacySortField() == null
                    ? policy.defaultDirection()
                    : directionOrFallback(policy.legacySortDirection(), policy.defaultDirection());
            orderings.put("native:" + expression, new OrderFragment(
                    expression + " " + direction,
                    List.of()
            ));
        }
        orderings.putIfAbsent("native:" + policy.stableExpression(), new OrderFragment(
                policy.stableExpression() + " " + policy.stableDirection(),
                List.of()
        ));
    }

    private PredicateFragment exists(String base, List<Object> baseArguments, String predicate, Object... values) {
        List<Object> arguments = new ArrayList<>(baseArguments);
        arguments.addAll(List.of(values));
        return new PredicateFragment("EXISTS (" + base + " AND " + predicate + ")", arguments);
    }

    private PredicateFragment notExists(String base, List<Object> baseArguments, String predicate, Object... values) {
        List<Object> arguments = new ArrayList<>(baseArguments);
        arguments.addAll(List.of(values));
        return new PredicateFragment("NOT EXISTS (" + base + " AND " + predicate + ")", arguments);
    }

    private Map<String, DefinitionRecord> definitionsByCode(Collection<DefinitionRecord> definitions) {
        Map<String, DefinitionRecord> fields = new LinkedHashMap<>();
        if (definitions != null) {
            definitions.stream()
                    .filter(definition -> "ENABLED".equals(definition.status()))
                    .forEach(definition -> fields.put(definition.fieldCode(), definition));
        }
        return fields;
    }

    private DefinitionRecord requireDefinition(Map<String, DefinitionRecord> fields, String rawField) {
        String code = customFieldCode(rawField);
        DefinitionRecord definition = fields.get(code);
        if (definition == null) throw badRequest("Unknown custom field: " + code);
        return definition;
    }

    private List<FilterCondition> parseFilters(String rawFilters) {
        if (rawFilters == null || rawFilters.isBlank()) return List.of();
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(rawFilters, new TypeReference<>() {});
            if (rows.size() > MAX_FILTERS) throw badRequest("Too many custom-field filters");
            List<FilterCondition> conditions = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String field = stringValue(row.get("field"));
                String operator = stringValue(row.get("operator"));
                if (field == null || operator == null) throw badRequest("Invalid custom-field filter");
                conditions.add(new FilterCondition(field, operator, row.get("value")));
            }
            return conditions;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw badRequest("Invalid custom-field filter JSON");
        }
    }

    private void requireSupportedOperator(CustomFieldType type, String operator) {
        Set<String> supported = switch (type) {
            case TEXT, LONG_TEXT, PHONE, EMAIL, URL -> Set.of(
                    "contains", "notContains", "equals", "notEquals", "empty", "notEmpty"
            );
            case NUMBER, CURRENCY -> Set.of(
                    "equals", "notEquals", "greaterThan", "greaterThanOrEqual", "lessThan",
                    "lessThanOrEqual", "between", "empty", "notEmpty"
            );
            case DATE, DATETIME -> Set.of(
                    "equals", "notEquals", "before", "after", "between", "dateRange",
                    "today", "thisWeek", "thisMonth", "empty", "notEmpty"
            );
            case SINGLE_SELECT -> Set.of(
                    "equals", "notEquals", "containsAny", "notContains", "empty", "notEmpty"
            );
            case MULTI_SELECT -> Set.of(
                    "equals", "notEquals", "containsAny", "containsAll", "notContains", "empty", "notEmpty"
            );
            case BOOLEAN -> Set.of("equals", "notEquals", "empty", "notEmpty");
            case ATTACHMENT -> Set.of("empty", "notEmpty");
            default -> Set.of();
        };
        if (!supported.contains(operator)) {
            throw badRequest("Unsupported operator " + operator + " for " + type.name());
        }
    }

    private String presentExpression(String column, CustomFieldType type) {
        if (TEXT_TYPES.contains(type) || type == CustomFieldType.SINGLE_SELECT) {
            return column + " IS NOT NULL AND TRIM(" + column + ") <> ''";
        }
        if (type == CustomFieldType.MULTI_SELECT) {
            return column + " IS NOT NULL AND JSON_LENGTH(" + column + ") > 0";
        }
        if (type == CustomFieldType.ATTACHMENT) {
            return "COALESCE(JSON_LENGTH(" + column + "), 0) > 0";
        }
        return column + " IS NOT NULL";
    }

    private Object comparableValue(Object rawValue) {
        try {
            return new BigDecimal(String.valueOf(rawValue));
        } catch (Exception exception) {
            throw badRequest("Invalid numeric custom-field value");
        }
    }

    private Object temporalValue(Object rawValue, boolean datetime) {
        try {
            String value = String.valueOf(rawValue);
            return datetime ? LocalDateTime.parse(value) : LocalDate.parse(value);
        } catch (Exception exception) {
            throw badRequest("Invalid date custom-field value");
        }
    }

    private List<?> requireRange(Object rawValue) {
        if (!(rawValue instanceof List<?> range) || range.size() != 2
                || isMissingValue(range.get(0)) || isMissingValue(range.get(1))) {
            throw badRequest("Custom-field range requires two values");
        }
        return range;
    }

    private List<String> stringValues(Object rawValue) {
        List<String> values;
        if (rawValue instanceof Collection<?> collection) {
            values = collection.stream().map(String::valueOf).map(String::trim).filter(value -> !value.isEmpty()).toList();
        } else {
            String value = stringValue(rawValue);
            values = value == null ? List.of() : List.of(value);
        }
        if (values.isEmpty()) throw badRequest("Custom-field option value is required");
        return values;
    }

    private void requireActiveOptions(DefinitionRecord definition, List<String> values) {
        Set<String> activeOptions = definition.options().stream()
                .filter(option -> "ENABLED".equals(option.status()))
                .map(option -> option.optionCode())
                .collect(java.util.stream.Collectors.toSet());
        if (!activeOptions.containsAll(values)) {
            throw badRequest("Unknown or disabled custom-field option");
        }
    }

    private boolean booleanValue(Object rawValue) {
        if (rawValue instanceof Boolean value) return value;
        String value = String.valueOf(rawValue).trim().toLowerCase(Locale.ROOT);
        if (Set.of("true", "1", "yes").contains(value)) return true;
        if (Set.of("false", "0", "no").contains(value)) return false;
        throw badRequest("Invalid boolean custom-field value");
    }

    private String customFieldCode(String rawField) {
        String field = stringValue(rawField);
        if (field == null) throw badRequest("Custom-field reference is required");
        if (field.startsWith("custom.")) return field.substring("custom.".length());
        if (field.startsWith("custom_")) return field;
        throw badRequest("Custom-field references must use the custom namespace");
    }

    private boolean isCustomReference(String rawField) {
        String field = stringValue(rawField);
        return field != null && (field.startsWith("custom.") || field.startsWith("custom_"));
    }

    private String direction(String rawDirection) {
        if ("asc".equalsIgnoreCase(rawDirection)) return "ASC";
        if ("desc".equalsIgnoreCase(rawDirection)) return "DESC";
        throw badRequest("Invalid custom-field sort direction");
    }

    private String directionOrFallback(String rawDirection, String fallback) {
        if ("asc".equalsIgnoreCase(rawDirection)) return "ASC";
        if ("desc".equalsIgnoreCase(rawDirection)) return "DESC";
        return fallback;
    }

    private void requireTrustedEntityExpression(String expression) {
        if (expression == null || !TRUSTED_ENTITY_EXPRESSION.matcher(expression).matches()) {
            throw new IllegalArgumentException("Unsafe entity identifier expression");
        }
    }

    private String stringValue(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean isMissingValue(Object value) {
        if (value == null) return true;
        if (value instanceof String text) return text.isBlank();
        if (value instanceof Collection<?> collection) return collection.isEmpty();
        return false;
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    private record FilterCondition(String field, String operator, Object value) {
    }

    private record PredicateFragment(String sql, List<Object> arguments) {
    }

    private record OrderFragment(String sql, List<Object> arguments) {
    }

    public record CompiledQuery(
            String predicateSql,
            List<Object> predicateArguments,
            List<String> orderExpressions,
            List<Object> orderArguments,
            int filterCount
    ) {
        public boolean hasPredicates() {
            return !predicateSql.isBlank();
        }

        public boolean hasOrderings() {
            return !orderExpressions.isEmpty();
        }
    }

    public record NativeOrderPolicy(
            Map<String, String> allowedFields,
            String legacySortField,
            String legacySortDirection,
            String defaultExpression,
            String defaultDirection,
            String stableExpression,
            String stableDirection
    ) {
        public NativeOrderPolicy {
            allowedFields = Map.copyOf(allowedFields);
            defaultDirection = normalizeTrustedDirection(defaultDirection, "DESC");
            stableDirection = normalizeTrustedDirection(stableDirection, "DESC");
        }

        public static NativeOrderPolicy of(
                Map<String, String> allowedFields,
                String legacySortField,
                String legacySortDirection,
                String defaultExpression,
                String stableExpression
        ) {
            return new NativeOrderPolicy(
                    allowedFields,
                    legacySortField,
                    legacySortDirection,
                    defaultExpression,
                    "DESC",
                    stableExpression,
                    "DESC"
            );
        }

        private static String normalizeTrustedDirection(String direction, String fallback) {
            if ("ASC".equalsIgnoreCase(direction)) return "ASC";
            if ("DESC".equalsIgnoreCase(direction)) return "DESC";
            return fallback;
        }
    }
}
