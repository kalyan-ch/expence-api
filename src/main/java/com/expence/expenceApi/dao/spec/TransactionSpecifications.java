package com.expence.expenceApi.dao.spec;

import com.expence.expenceApi.model.finance.Transaction;
import com.expence.expenceApi.model.finance.TransactionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /** Every argument is optional; a null one contributes no predicate. */
    public static Specification<Transaction> filter(LocalDate from, LocalDate to, UUID accountId,
                                                    UUID categoryId, TransactionType type) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("occurredOn"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("occurredOn"), to));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("account").get("id"), accountId));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
