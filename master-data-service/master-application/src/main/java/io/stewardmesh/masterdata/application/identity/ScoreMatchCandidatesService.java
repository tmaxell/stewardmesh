package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.application.port.in.ScoreMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.LoadMatchProfiles;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.application.port.out.MatchScoringTelemetry;
import io.stewardmesh.masterdata.application.port.out.StoreMatchEvaluation;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.PartyMatchProfile;
import io.stewardmesh.masterdata.domain.identity.SiteMatchProfile;
import io.stewardmesh.masterdata.domain.identity.SupplierMatchInput;
import io.stewardmesh.masterdata.domain.identity.SupplierMatchScorer;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Scores a bounded candidate set and persists complete, non-sensitive feature evidence. */
public final class ScoreMatchCandidatesService implements ScoreMatchCandidates {

    private final LoadSourceRecord sourceRecords;
    private final LoadMatchProfiles profiles;
    private final StoreMatchEvaluation evaluations;
    private final MatchScoringTelemetry telemetry;
    private final SupplierMatchScorer scorer;
    private final Clock clock;

    public ScoreMatchCandidatesService(
            LoadSourceRecord sourceRecords,
            LoadMatchProfiles profiles,
            StoreMatchEvaluation evaluations,
            MatchScoringTelemetry telemetry,
            SupplierMatchScorer scorer,
            Clock clock) {
        this.sourceRecords = Objects.requireNonNull(sourceRecords, "sourceRecords must not be null");
        this.profiles = Objects.requireNonNull(profiles, "profiles must not be null");
        this.evaluations = Objects.requireNonNull(evaluations, "evaluations must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.scorer = Objects.requireNonNull(scorer, "scorer must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public MatchEvaluation execute(ScoreMatchCandidatesCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return telemetry.measure(() -> evaluate(command.candidateBlocks()));
    }

    private MatchEvaluation evaluate(MatchCandidateBlocks blocks) {
        SourceRecord sourceRecord = sourceRecords
                .findByIdentity(blocks.sourceRecordIdentity())
                .orElseThrow(() -> new SourceRecordNotFoundException(blocks.sourceRecordIdentity()));
        SupplierMatchInput source = input(sourceRecord);
        Map<SupplierPartyId, PartyMatchProfile> partyProfiles = partyProfiles(blocks);
        Map<SupplierSiteId, SiteMatchProfile> siteProfiles = siteProfiles(blocks);

        List<MatchDecision> partyDecisions = blocks.parties().candidates().stream()
                .map(block -> scorer.decide(scorer.scoreParty(source, partyProfiles.get(block.partyId()))))
                .toList();
        List<MatchDecision> siteDecisions = blocks.sites().candidates().stream()
                .map(block -> scorer.decide(scorer.scoreSite(source, siteProfiles.get(block.siteId()))))
                .toList();
        var evaluation = new MatchEvaluation(
                sourceRecord.identity(),
                SupplierMatchScorer.RULESET.id(),
                clock.instant(),
                partyDecisions,
                siteDecisions);
        evaluations.save(evaluation);
        telemetry.record(evaluation);
        return evaluation;
    }

    private Map<SupplierPartyId, PartyMatchProfile> partyProfiles(MatchCandidateBlocks blocks) {
        Set<SupplierPartyId> requested = blocks.parties().candidates().stream()
                .map(PartyCandidateBlock::partyId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (requested.isEmpty()) {
            return Map.of();
        }
        List<PartyMatchProfile> loaded = List.copyOf(profiles.loadParties(requested));
        Map<SupplierPartyId, PartyMatchProfile> indexed = new HashMap<>();
        loaded.forEach(profile -> {
            if (indexed.put(profile.partyId(), profile) != null) {
                throw new MatchProfileException("party profile loader returned a duplicate candidate");
            }
        });
        requireExactProfiles(requested, indexed.keySet(), "party");
        return indexed;
    }

    private Map<SupplierSiteId, SiteMatchProfile> siteProfiles(MatchCandidateBlocks blocks) {
        Set<SupplierSiteId> requested = blocks.sites().candidates().stream()
                .map(SiteCandidateBlock::siteId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (requested.isEmpty()) {
            return Map.of();
        }
        List<SiteMatchProfile> loaded = List.copyOf(profiles.loadSites(requested));
        Map<SupplierSiteId, SiteMatchProfile> indexed = new HashMap<>();
        loaded.forEach(profile -> {
            if (indexed.put(profile.siteId(), profile) != null) {
                throw new MatchProfileException("site profile loader returned a duplicate candidate");
            }
        });
        requireExactProfiles(requested, indexed.keySet(), "site");
        blocks.sites().candidates().forEach(block -> {
            if (!block.partyId().equals(indexed.get(block.siteId()).partyId())) {
                throw new MatchProfileException("site profile ownership differs from blocking evidence");
            }
        });
        return indexed;
    }

    private static void requireExactProfiles(Set<?> requested, Set<?> loaded, String entityType) {
        if (!new HashSet<>(requested).equals(new HashSet<>(loaded))) {
            throw new MatchProfileException(entityType + " profile loader returned an incomplete result");
        }
    }

    private static SupplierMatchInput input(SourceRecord sourceRecord) {
        Map<String, String> values = sourceRecord.canonicalValues();
        return new SupplierMatchInput(
                required(values, "inn"),
                values.get("ogrn"),
                required(values, "legal_name"),
                values.get("kpp"),
                values.get("site_code"),
                required(values, "country_code"),
                required(values, "city"),
                required(values, "address_line"));
    }

    private static String required(Map<String, String> values, String field) {
        String value = values.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("canonical source field is required for scoring: " + field);
        }
        return value;
    }
}
