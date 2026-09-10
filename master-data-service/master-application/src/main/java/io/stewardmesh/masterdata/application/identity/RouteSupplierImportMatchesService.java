package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.application.intake.SupplierImportNotFoundException;
import io.stewardmesh.masterdata.application.port.in.GenerateMatchCandidates;
import io.stewardmesh.masterdata.application.port.in.RouteSupplierImportMatches;
import io.stewardmesh.masterdata.application.port.in.ScoreMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.LoadImportMatchWork;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluationSummary;
import io.stewardmesh.masterdata.application.port.out.StoreStewardshipCase;
import io.stewardmesh.masterdata.domain.identity.SupplierMatchScorer;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.InvalidImportTransitionException;
import io.stewardmesh.masterdata.domain.stewardship.StewardshipCase;
import io.stewardmesh.masterdata.domain.stewardship.StewardshipCaseReason;
import java.util.List;
import java.util.Objects;

/** Resumable handoff from validated intake assertions to review-ready match outcomes. */
public final class RouteSupplierImportMatchesService implements RouteSupplierImportMatches {

    private final ImportJobRepository importJobs;
    private final LoadImportMatchWork matchWork;
    private final GenerateMatchCandidates generateCandidates;
    private final ScoreMatchCandidates scoreCandidates;
    private final LoadMatchEvaluationSummary evaluationSummaries;
    private final StoreStewardshipCase stewardshipCases;

    public RouteSupplierImportMatchesService(
            ImportJobRepository importJobs,
            LoadImportMatchWork matchWork,
            GenerateMatchCandidates generateCandidates,
            ScoreMatchCandidates scoreCandidates,
            LoadMatchEvaluationSummary evaluationSummaries,
            StoreStewardshipCase stewardshipCases) {
        this.importJobs = Objects.requireNonNull(importJobs, "importJobs must not be null");
        this.matchWork = Objects.requireNonNull(matchWork, "matchWork must not be null");
        this.generateCandidates =
                Objects.requireNonNull(generateCandidates, "generateCandidates must not be null");
        this.scoreCandidates =
                Objects.requireNonNull(scoreCandidates, "scoreCandidates must not be null");
        this.evaluationSummaries =
                Objects.requireNonNull(evaluationSummaries, "evaluationSummaries must not be null");
        this.stewardshipCases =
                Objects.requireNonNull(stewardshipCases, "stewardshipCases must not be null");
    }

    @Override
    public RouteSupplierImportMatchesResult execute(RouteSupplierImportMatchesCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ImportJob job = importJobs.findById(command.importJobId())
                .orElseThrow(() -> new SupplierImportNotFoundException(command.importJobId()));
        if (job.status() == ImportStatus.MATCHED || job.status() == ImportStatus.REVIEW_REQUIRED) {
            return result(job);
        }
        if (job.status() != ImportStatus.VALIDATED && job.status() != ImportStatus.MATCHING) {
            throw new InvalidImportTransitionException(job.status(), ImportStatus.MATCHING);
        }

        List<ImportMatchWorkItem> work = List.copyOf(matchWork.load(command.importJobId()));
        work.stream()
                .filter(item -> !item.latestVersion())
                .findFirst()
                .ifPresent(item -> {
                    throw new StaleSourceRecordException(item.sourceRecordIdentity());
                });

        ImportJob matching = job.status() == ImportStatus.MATCHING ? job : job.startMatching();
        if (job.status() != ImportStatus.MATCHING) {
            importJobs.save(matching);
        }

        boolean reviewRequired = false;
        for (ImportMatchWorkItem item : work) {
            MatchEvaluationSummary summary = evaluationSummaries
                    .find(item.sourceRecordIdentity(), SupplierMatchScorer.RULESET.id())
                    .orElseGet(() -> score(item, command.candidateLimit()));
            if (summary.reviewRequired()) {
                stewardshipCases.save(new StewardshipCase(
                        item.sourceRecordIdentity(),
                        SupplierMatchScorer.RULESET.id(),
                        summary.hardConflict()
                                ? StewardshipCaseReason.AUTHORITATIVE_CONFLICT
                                : StewardshipCaseReason.AMBIGUOUS_MATCH,
                        summary.evaluatedAt()));
                reviewRequired = true;
            }
        }

        ImportJob completed = matching.finishMatching(reviewRequired);
        importJobs.save(completed);
        return result(completed);
    }

    private MatchEvaluationSummary score(ImportMatchWorkItem item, int candidateLimit) {
        MatchCandidateBlocks candidates = generateCandidates.execute(
                new GenerateMatchCandidatesCommand(item.sourceRecordIdentity(), candidateLimit));
        MatchEvaluation evaluation = scoreCandidates.execute(new ScoreMatchCandidatesCommand(candidates));
        return MatchEvaluationSummary.from(evaluation);
    }

    private static RouteSupplierImportMatchesResult result(ImportJob job) {
        return new RouteSupplierImportMatchesResult(job.id(), job.status());
    }
}
