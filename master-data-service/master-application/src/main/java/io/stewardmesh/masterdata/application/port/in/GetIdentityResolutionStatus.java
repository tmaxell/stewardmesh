package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionStatus;

public interface GetIdentityResolutionStatus
        extends UseCase<IdentityResolutionKey, IdentityResolutionStatus> {}
