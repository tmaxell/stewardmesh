package io.stewardmesh.masterdata.application.goldenrecord;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.port.in.ProjectSourceRecord;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordState;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.application.port.out.StoreGoldenRecordProjection;
import io.stewardmesh.masterdata.domain.goldenrecord.AssociationEvidence;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordProjector;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenSourceAssertion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociation;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SourcePriority;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Deterministically creates or recalculates golden records from persisted match evidence. */
public final class ProjectSourceRecordService implements ProjectSourceRecord {

    private static final SourcePriority DEFAULT_SOURCE_PRIORITY = new SourcePriority(100);

    private final LoadMatchEvaluation evaluations;
    private final LoadSourceRecord sourceRecords;
    private final LoadGoldenRecordState states;
    private final StoreGoldenRecordProjection projections;
    private final ApplicationTransaction transaction;
    private final GoldenRecordProjector projector;

    public ProjectSourceRecordService(
            LoadMatchEvaluation evaluations,
            LoadSourceRecord sourceRecords,
            LoadGoldenRecordState states,
            StoreGoldenRecordProjection projections,
            ApplicationTransaction transaction,
            GoldenRecordProjector projector) {
        this.evaluations = Objects.requireNonNull(evaluations, "evaluations must not be null");
        this.sourceRecords = Objects.requireNonNull(sourceRecords, "sourceRecords must not be null");
        this.states = Objects.requireNonNull(states, "states must not be null");
        this.projections = Objects.requireNonNull(projections, "projections must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.projector = Objects.requireNonNull(projector, "projector must not be null");
    }

    @Override
    public void execute(IdentityResolutionKey key) {
        Objects.requireNonNull(key, "key must not be null");
        var evaluation = evaluations.find(key)
                .orElseThrow(() -> new IllegalStateException("match evaluation is unavailable"));
        boolean unsafe = java.util.stream.Stream.concat(
                        evaluation.partyDecisions().stream(), evaluation.siteDecisions().stream())
                .anyMatch(decision -> decision.hardConflict()
                        || decision.outcome() == MatchOutcome.REVIEW);
        if (unsafe) {
            throw new IllegalArgumentException("review-required evaluation cannot be projected");
        }
        SourceRecord source = sourceRecords
                .findByIdentity(key.sourceRecordIdentity())
                .orElseThrow(() -> new IllegalStateException("source record is unavailable"));
        transaction.execute(() -> {
            project(source, evaluation);
            return null;
        });
    }

    private void project(
            SourceRecord source,
            io.stewardmesh.masterdata.application.identity.MatchEvaluation evaluation) {
        Optional<MatchDecision> partyMatch = autoLink(evaluation.partyDecisions());
        SupplierPartyId partyId = partyMatch
                .map(decision -> new SupplierPartyId(decision.candidateId()))
                .orElseGet(() -> new SupplierPartyId(stableId(source, "party")));
        Optional<GoldenRecordState> current = states.findByPartyId(partyId);
        if (current.stream()
                .flatMap(state -> state.assertions().stream())
                .anyMatch(assertion -> assertion.sourceRecord().equals(source.identity()))) {
            return;
        }

        Optional<MatchDecision> siteMatch = current.flatMap(state -> autoLink(
                evaluation.siteDecisions().stream()
                        .filter(decision -> state.sites().containsKey(
                                new SupplierSiteId(decision.candidateId())))
                        .toList()));
        boolean sitePresent = hasSite(source);
        Optional<SupplierSiteId> siteId = sitePresent
                ? Optional.of(siteMatch
                        .map(decision -> new SupplierSiteId(decision.candidateId()))
                        .orElseGet(() -> new SupplierSiteId(stableId(source, "site"))))
                : Optional.empty();
        Optional<SupplierAddressId> addressId = siteId.map(id -> siteMatch
                .flatMap(ignored -> current)
                .map(state -> state.sites().get(id).addressId())
                .orElseGet(() -> new SupplierAddressId(stableId(source, "address"))));

        var association = new SourceAssociation(
                new SourceAssociationId(stableId(source, "association")),
                source.identity(),
                partyId,
                addressId,
                siteId,
                partyMatch.map(AssociationEvidence::autoLinked)
                        .orElseGet(() -> AssociationEvidence.newEntity(evaluation.rulesetId())),
                siteId.map(ignored -> siteMatch.map(AssociationEvidence::autoLinked)
                        .orElseGet(() -> AssociationEvidence.newEntity(evaluation.rulesetId()))),
                evaluation.evaluatedAt(),
                Optional.empty());
        var assertions = new ArrayList<GoldenSourceAssertion>();
        current.ifPresent(state -> assertions.addAll(state.assertions()));
        assertions.add(new GoldenSourceAssertion(
                source.identity(),
                source.ingestedAt(),
                DEFAULT_SOURCE_PRIORITY,
                association,
                goldenValues(source)));

        long partyVersion = current.map(GoldenRecordState::partyVersion).orElse(0L) + 1;
        var party = projector.projectParty(
                partyId,
                new GoldenRecordVersion(partyVersion),
                evaluation.evaluatedAt(),
                assertions);
        var addresses = addressId.stream()
                .map(id -> projector.projectAddress(
                        id,
                        partyId,
                        new GoldenRecordVersion(current
                                        .map(state -> state.addressVersions().getOrDefault(id, 0L))
                                        .orElse(0L)
                                + 1),
                        evaluation.evaluatedAt(),
                        assertions))
                .toList();
        var sites = siteId.stream()
                .map(id -> projector.projectSite(
                        id,
                        partyId,
                        addressId.orElseThrow(),
                        new GoldenRecordVersion(current
                                        .map(state -> Optional.ofNullable(state.sites().get(id))
                                                .map(GoldenRecordState.SiteState::version)
                                                .orElse(0L))
                                        .orElse(0L)
                                + 1),
                        evaluation.evaluatedAt(),
                        assertions))
                .toList();
        projections.save(new GoldenRecordProjection(
                party,
                addresses,
                sites,
                assertions.stream().map(GoldenSourceAssertion::association).toList()));
    }

    private static Optional<MatchDecision> autoLink(List<MatchDecision> decisions) {
        return decisions.stream()
                .filter(decision -> decision.outcome() == MatchOutcome.AUTO_LINK)
                .filter(decision -> !decision.hardConflict())
                .findFirst();
    }

    private static boolean hasSite(SourceRecord source) {
        return value(source, "kpp") != null
                || value(source, "site_code") != null
                || value(source, "procurement_bu_code") != null
                || value(source, "site_purpose") != null;
    }

    private static EnumMap<GoldenAttributeName, String> goldenValues(SourceRecord source) {
        var values = new EnumMap<GoldenAttributeName, String>(GoldenAttributeName.class);
        for (var name : GoldenAttributeName.values()) {
            String value = value(source, name.sourceField());
            if (value != null) {
                values.put(name, value);
            }
        }
        return values;
    }

    private static String value(SourceRecord source, String field) {
        String value = source.canonicalValues().get(field);
        return value == null || value.isBlank() ? null : value;
    }

    private static UUID stableId(SourceRecord source, String target) {
        var identity = source.identity();
        String name = identity.originSystem().value() + '\u001f' + identity.sourceRecordId()
                + '\u001f' + identity.sourceVersion() + '\u001f' + target;
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
