package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.identity.MatchEvaluation;
import java.util.Optional;

public interface LoadMatchEvaluation {

    Optional<MatchEvaluation> find(IdentityResolutionKey key);
}
