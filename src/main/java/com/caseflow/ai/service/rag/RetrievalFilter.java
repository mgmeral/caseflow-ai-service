package com.caseflow.ai.service.rag;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Metadata constraints for a vector search. Every non-null field must match; a null or empty
 * list means "no constraint on this field".
 *
 * <p>The same constraints are applied twice: pushed down to Qdrant as a filter expression, and
 * re-checked on the returned documents ({@link #matches}). A caller that passes a customer or
 * group scope must never see a document outside it, even if the store ignored the filter.
 *
 * @param sourceType       e.g. {@code TICKET}, {@code POLICY}
 * @param customerIds      allowed {@code customerId} values; include {@link #GLOBAL} for shared docs
 * @param groupIds         allowed {@code groupId} values
 * @param statuses         allowed {@code status} values
 * @param excludeSourceIds {@code sourceId}s to leave out (e.g. the ticket being worked on)
 */
public record RetrievalFilter(
        String sourceType,
        List<String> customerIds,
        List<String> groupIds,
        List<String> statuses,
        List<String> excludeSourceIds
) {

    /** {@code customerId} stored on documents that apply to every customer (e.g. general policies). */
    public static final String GLOBAL = "GLOBAL";

    public static RetrievalFilter ofSourceType(String sourceType) {
        return new RetrievalFilter(sourceType, null, null, null, null);
    }

    /**
     * Policies a given customer may see: GLOBAL ones plus that customer's own. Without a
     * customerId only GLOBAL policies are returned — never another customer's.
     */
    public static RetrievalFilter policiesFor(String customerId) {
        List<String> customers = customerId != null && !customerId.isBlank()
                ? List.of(GLOBAL, customerId) : List.of(GLOBAL);
        return new RetrievalFilter("POLICY", customers, null, null, null);
    }

    public boolean isEmpty() {
        return sourceType == null && isEmpty(customerIds) && isEmpty(groupIds)
                && isEmpty(statuses) && isEmpty(excludeSourceIds);
    }

    /** The constraints as a Spring AI filter expression, or {@code null} if there are none. */
    public Filter.Expression toExpression() {
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<FilterExpressionBuilder.Op> ops = new ArrayList<>();
        if (sourceType != null) ops.add(b.eq("sourceType", sourceType.toUpperCase()));
        if (!isEmpty(customerIds)) ops.add(b.in("customerId", customerIds.toArray()));
        if (!isEmpty(groupIds)) ops.add(b.in("groupId", groupIds.toArray()));
        if (!isEmpty(statuses)) ops.add(b.in("status", statuses.toArray()));
        if (!isEmpty(excludeSourceIds)) ops.add(b.nin("sourceId", excludeSourceIds.toArray()));
        if (ops.isEmpty()) return null;
        FilterExpressionBuilder.Op all = ops.get(0);
        for (int i = 1; i < ops.size(); i++) all = b.and(all, ops.get(i));
        return all.build();
    }

    /** Same constraints, evaluated on a returned document's metadata. */
    public boolean matches(Map<String, Object> metadata) {
        if (sourceType != null && !sourceType.equalsIgnoreCase(str(metadata.get("sourceType")))) return false;
        if (!isEmpty(customerIds) && !contains(customerIds, metadata.get("customerId"))) return false;
        if (!isEmpty(groupIds) && !contains(groupIds, metadata.get("groupId"))) return false;
        if (!isEmpty(statuses) && !contains(statuses, metadata.get("status"))) return false;
        return isEmpty(excludeSourceIds) || !contains(excludeSourceIds, metadata.get("sourceId"));
    }

    /** Null-safe: a document missing the field never matches (immutable lists reject contains(null)). */
    private static boolean contains(List<String> allowed, Object value) {
        return value != null && allowed.contains(value.toString());
    }

    private static String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private static boolean isEmpty(List<String> list) {
        return list == null || list.isEmpty();
    }
}
