package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import java.util.function.Supplier;

final class DirectApplicationTransaction implements ApplicationTransaction {

    @Override
    public <T> T execute(Supplier<T> operation) {
        return operation.get();
    }
}
