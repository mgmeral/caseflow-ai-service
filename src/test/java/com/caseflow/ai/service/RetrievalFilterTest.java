package com.caseflow.ai.service;

import com.caseflow.ai.service.rag.RetrievalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalFilterTest {

    private static final RetrievalFilter SCOPED = new RetrievalFilter(
            "TICKET", List.of("c1", "c2"), List.of("g1"), List.of("RESOLVED"), List.of("t-self"));

    @Test
    void matches_acceptsDocumentInsideEveryConstraint() {
        assertThat(SCOPED.matches(Map.of("sourceType", "TICKET", "customerId", "c2",
                "groupId", "g1", "status", "RESOLVED", "sourceId", "t-9"))).isTrue();
    }

    @Test
    void matches_rejectsOtherCustomer_otherGroup_otherStatus_andExcludedSource() {
        Map<String, Object> ok = Map.of("sourceType", "TICKET", "customerId", "c1",
                "groupId", "g1", "status", "RESOLVED", "sourceId", "t-9");

        assertThat(SCOPED.matches(with(ok, "customerId", "c3"))).isFalse();
        assertThat(SCOPED.matches(with(ok, "groupId", "g2"))).isFalse();
        assertThat(SCOPED.matches(with(ok, "status", "OPEN"))).isFalse();
        assertThat(SCOPED.matches(with(ok, "sourceId", "t-self"))).isFalse();
        assertThat(SCOPED.matches(with(ok, "sourceType", "POLICY"))).isFalse();
    }

    @Test
    void matches_rejectsDocumentMissingAScopedField() {
        // A ticket indexed without customerId must not leak into a customer-scoped search.
        assertThat(SCOPED.matches(Map.of("sourceType", "TICKET", "groupId", "g1",
                "status", "RESOLVED", "sourceId", "t-9"))).isFalse();
    }

    @Test
    void policiesFor_customer_allowsGlobalAndOwnOnly() {
        RetrievalFilter f = RetrievalFilter.policiesFor("c1");

        assertThat(f.matches(Map.of("sourceType", "POLICY", "customerId", RetrievalFilter.GLOBAL))).isTrue();
        assertThat(f.matches(Map.of("sourceType", "POLICY", "customerId", "c1"))).isTrue();
        assertThat(f.matches(Map.of("sourceType", "POLICY", "customerId", "c2"))).isFalse();
    }

    @Test
    void policiesFor_noCustomer_allowsGlobalOnly() {
        RetrievalFilter f = RetrievalFilter.policiesFor(null);

        assertThat(f.matches(Map.of("sourceType", "POLICY", "customerId", RetrievalFilter.GLOBAL))).isTrue();
        assertThat(f.matches(Map.of("sourceType", "POLICY", "customerId", "c1"))).isFalse();
    }

    @Test
    void toExpression_combinesAllConstraints() {
        Filter.Expression e = SCOPED.toExpression();

        assertThat(e).isNotNull();
        String s = e.toString();
        assertThat(s).contains("sourceType", "TICKET", "customerId", "c1", "c2", "groupId",
                "status", "RESOLVED", "sourceId", "t-self", "NIN");
    }

    @Test
    void toExpression_isNullWithoutConstraints() {
        assertThat(RetrievalFilter.ofSourceType(null).toExpression()).isNull();
        assertThat(RetrievalFilter.ofSourceType(null).isEmpty()).isTrue();
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> copy = new java.util.HashMap<>(base);
        copy.put(key, value);
        return copy;
    }
}
