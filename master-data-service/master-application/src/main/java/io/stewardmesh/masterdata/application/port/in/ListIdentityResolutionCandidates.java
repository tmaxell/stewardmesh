package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidatePage;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidateQuery;

public interface ListIdentityResolutionCandidates
        extends UseCase<IdentityResolutionCandidateQuery, IdentityResolutionCandidatePage> {}
