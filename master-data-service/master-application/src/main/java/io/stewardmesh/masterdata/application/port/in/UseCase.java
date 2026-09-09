package io.stewardmesh.masterdata.application.port.in;

/** Framework-neutral boundary for an application use case. */
@FunctionalInterface
public interface UseCase<C, R> {

    R execute(C command);
}
