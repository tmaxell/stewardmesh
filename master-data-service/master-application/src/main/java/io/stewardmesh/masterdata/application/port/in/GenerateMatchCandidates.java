package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.identity.GenerateMatchCandidatesCommand;
import io.stewardmesh.masterdata.application.identity.MatchCandidateBlocks;

/** Generates bounded party and site candidate blocks without scoring or mutation. */
public interface GenerateMatchCandidates
        extends UseCase<GenerateMatchCandidatesCommand, MatchCandidateBlocks> {}
