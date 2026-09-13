package io.stewardmesh.masterdata.application;

import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import java.util.function.Supplier;

/** Runs the operation inline so use-case tests stay free of a transaction manager. */
public final class DirectApplicationTransaction implements ApplicationTransaction {

    @Override
    public <T> T execute(Supplier<T> operation) {
        return operation.get();
    }
}
