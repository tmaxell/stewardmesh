package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.audit.AuditEvent;

@FunctionalInterface
public interface AuditEventRepository {

    void append(AuditEvent event);
}
