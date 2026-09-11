package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;

/** Applies one conflict-free identity-resolution result to golden projections. */
public interface ProjectSourceRecord {

    void execute(IdentityResolutionKey key);
}
