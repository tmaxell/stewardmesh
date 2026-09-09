package io.stewardmesh.masterdata.application.port.out;

import java.util.function.Supplier;

/** Runs one database-only application operation atomically. */
@FunctionalInterface
public interface ApplicationTransaction {

    <T> T execute(Supplier<T> operation);
}
