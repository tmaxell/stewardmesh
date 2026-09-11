package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.identity.MatchEvaluation;

public interface StoreMatchEvaluation {

    void save(MatchEvaluation evaluation);
}
