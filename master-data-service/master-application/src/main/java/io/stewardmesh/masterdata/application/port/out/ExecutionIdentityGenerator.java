package io.stewardmesh.masterdata.application.port.out;

import java.util.UUID;

/** Generates server-owned execution, correlation, audit and business-event identities. */
@FunctionalInterface
public interface ExecutionIdentityGenerator {

    UUID nextId();
}
