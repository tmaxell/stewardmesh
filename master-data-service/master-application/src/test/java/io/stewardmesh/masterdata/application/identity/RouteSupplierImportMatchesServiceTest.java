package io.stewardmesh.masterdata.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchFeatureCode;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.MatchSignal;
import io.stewardmesh.masterdata.domain.identity.SupplierMatchScorer;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.stewardship.StewardshipCase;
import io.stewardmesh.masterdata.domain.stewardship.StewardshipCaseReason;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RouteSupplierImportMatchesServiceTest {

    private static final Instant EVALUATED_AT = Instant.parse("2026-09-10T10:00:00Z");
    private static final SourceRecordIdentity SOURCE = new SourceRecordIdentity(
            new SourceSystemRef("ERP"), "supplier-42", 2);

    @Test
    void routesAmbiguousEvaluationToAReviewCase() {
        var state = new State();
        var cases = new ArrayList<StewardshipCase>();
        var service = service(state, List.of(new ImportMatchWorkItem(SOURCE, true)),
                MatchOutcome.REVIEW, false, cases, new AtomicInteger());

        var result = service.execute(new RouteSupplierImportMatchesCommand(state.job.id(), 25));

        assertEquals(ImportStatus.REVIEW_REQUIRED, result.status());
        assertEquals(ImportStatus.REVIEW_REQUIRED, state.job.status());
        assertEquals(List.of(ImportStatus.MATCHING, ImportStatus.REVIEW_REQUIRED), state.savedStatuses);
        assertEquals(1, cases.size());
        assertEquals(StewardshipCaseReason.AMBIGUOUS_MATCH, cases.getFirst().reason());
    }

    @Test
    void duplicateInvocationReturnsCompletedOutcomeWithoutRepeatingWork() {
        var state = new State();
        var calls = new AtomicInteger();
        var service = service(state, List.of(new ImportMatchWorkItem(SOURCE, true)),
                MatchOutcome.AUTO_LINK, false, new ArrayList<>(), calls);

        service.execute(new RouteSupplierImportMatchesCommand(state.job.id(), 25));
        var replay = service.execute(new RouteSupplierImportMatchesCommand(state.job.id(), 25));

        assertEquals(ImportStatus.MATCHED, replay.status());
        assertEquals(1, calls.get());
        assertEquals(List.of(ImportStatus.MATCHING, ImportStatus.MATCHED), state.savedStatuses);
    }

    @Test
    void resumesAfterCasePersistenceFailureWithoutRescoring() {
        var state = new State();
        var calls = new AtomicInteger();
        var summaries = new HashMap<SourceRecordIdentity, MatchEvaluationSummary>();
        var failFirst = new AtomicBoolean(true);
        var cases = new ArrayList<StewardshipCase>();
        var blocks = emptyBlocks();
        var service = new RouteSupplierImportMatchesService(
                state,
                ignored -> List.of(new ImportMatchWorkItem(SOURCE, true)),
                ignored -> blocks,
                ignored -> {
                    calls.incrementAndGet();
                    MatchEvaluation evaluation = evaluation(MatchOutcome.NO_MATCH, true);
                    summaries.put(SOURCE, MatchEvaluationSummary.from(evaluation));
                    return evaluation;
                },
                (identity, ruleset) -> Optional.ofNullable(summaries.get(identity)),
                reviewCase -> {
                    if (failFirst.getAndSet(false)) {
                        throw new IllegalStateException("synthetic case-store failure");
                    }
                    cases.add(reviewCase);
                });

        assertThrows(
                IllegalStateException.class,
                () -> service.execute(new RouteSupplierImportMatchesCommand(state.job.id(), 25)));
        assertEquals(ImportStatus.MATCHING, state.job.status());

        var recovered = service.execute(new RouteSupplierImportMatchesCommand(state.job.id(), 25));

        assertEquals(ImportStatus.REVIEW_REQUIRED, recovered.status());
        assertEquals(1, calls.get());
        assertEquals(StewardshipCaseReason.AUTHORITATIVE_CONFLICT, cases.getFirst().reason());
    }

    @Test
    void rejectsStaleSourceBeforeStartingMatching() {
        var state = new State();
        var calls = new AtomicInteger();
        var service = service(state, List.of(new ImportMatchWorkItem(SOURCE, false)),
                MatchOutcome.AUTO_LINK, false, new ArrayList<>(), calls);

        assertThrows(
                StaleSourceRecordException.class,
                () -> service.execute(new RouteSupplierImportMatchesCommand(state.job.id(), 25)));

        assertEquals(ImportStatus.VALIDATED, state.job.status());
        assertEquals(0, calls.get());
        assertEquals(List.of(), state.savedStatuses);
    }

    private static RouteSupplierImportMatchesService service(
            State state,
            List<ImportMatchWorkItem> work,
            MatchOutcome outcome,
            boolean hardConflict,
            List<StewardshipCase> cases,
            AtomicInteger calls) {
        MatchCandidateBlocks blocks = emptyBlocks();
        return new RouteSupplierImportMatchesService(
                state,
                ignored -> work,
                ignored -> blocks,
                ignored -> {
                    calls.incrementAndGet();
                    return evaluation(outcome, hardConflict);
                },
                (identity, ruleset) -> Optional.empty(),
                cases::add);
    }

    private static MatchCandidateBlocks emptyBlocks() {
        return new MatchCandidateBlocks(
                SOURCE,
                new CandidateBlockPage<>(25, false, List.of()),
                new CandidateBlockPage<>(25, false, List.of()));
    }

    private static MatchEvaluation evaluation(MatchOutcome outcome, boolean hardConflict) {
        var feature = new MatchFeature(
                hardConflict
                        ? MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT
                        : MatchFeatureCode.INN_EXACT,
                hardConflict ? MatchSignal.CONFLICT : MatchSignal.MATCH,
                hardConflict ? -4_000 : 4_000);
        var decision = new MatchDecision(
                MatchEntityType.PARTY,
                UUID.randomUUID(),
                outcome,
                outcome == MatchOutcome.AUTO_LINK ? 8_500 : 5_500,
                SupplierMatchScorer.RULESET.id(),
                hardConflict,
                List.of(feature));
        return new MatchEvaluation(
                SOURCE, SupplierMatchScorer.RULESET.id(), EVALUATED_AT, List.of(decision), List.of());
    }

    private static final class State implements ImportJobRepository {

        private ImportJob job = validatedJob();
        private final List<ImportStatus> savedStatuses = new ArrayList<>();

        @Override
        public Optional<ImportJob> findById(ImportJobId importJobId) {
            return job.id().equals(importJobId) ? Optional.of(job) : Optional.empty();
        }

        @Override
        public void save(ImportJob importJob) {
            job = importJob;
            savedStatuses.add(importJob.status());
        }
    }

    private static ImportJob validatedJob() {
        return ImportJob.received(
                        new ImportJobId(UUID.randomUUID()),
                        new IntakeArtifactId(UUID.randomUUID()),
                        new SourceSystemRef("ERP"),
                        EVALUATED_AT.minusSeconds(60))
                .startParsing()
                .finishParsing(1)
                .startValidation()
                .finishValidation(1, 0, 0, 0);
    }
}
