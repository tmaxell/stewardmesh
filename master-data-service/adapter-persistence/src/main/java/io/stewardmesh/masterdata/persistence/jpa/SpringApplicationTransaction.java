package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/** Spring-backed database transaction boundary used by framework-free application services. */
public final class SpringApplicationTransaction implements ApplicationTransaction {

    private final TransactionTemplate transactionTemplate;

    public SpringApplicationTransaction(TransactionTemplate transactionTemplate) {
        this.transactionTemplate =
                Objects.requireNonNull(transactionTemplate, "transactionTemplate must not be null");
    }

    @Override
    public <T> T execute(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        return transactionTemplate.execute(status -> operation.get());
    }
}
