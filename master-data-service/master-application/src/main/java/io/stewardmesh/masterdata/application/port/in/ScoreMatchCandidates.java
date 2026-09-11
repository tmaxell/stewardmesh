package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.identity.MatchEvaluation;
import io.stewardmesh.masterdata.application.identity.ScoreMatchCandidatesCommand;

public interface ScoreMatchCandidates
        extends UseCase<ScoreMatchCandidatesCommand, MatchEvaluation> {}
