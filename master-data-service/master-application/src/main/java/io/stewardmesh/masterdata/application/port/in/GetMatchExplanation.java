package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.identity.MatchExplanationQuery;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;

public interface GetMatchExplanation extends UseCase<MatchExplanationQuery, MatchDecision> {}
