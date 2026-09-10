package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.application.port.in.GenerateMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.BlockMatchCandidates;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Builds deterministic, bounded candidate blocks; scoring remains a separate domain step. */
public final class GenerateMatchCandidatesService implements GenerateMatchCandidates {

    private final LoadSourceRecord sourceRecords;
    private final BlockMatchCandidates candidateBlocks;
    private final CandidateBlockingPolicy policy;

    public GenerateMatchCandidatesService(
            LoadSourceRecord sourceRecords,
            BlockMatchCandidates candidateBlocks,
            CandidateBlockingPolicy policy) {
        this.sourceRecords = Objects.requireNonNull(sourceRecords, "sourceRecords must not be null");
        this.candidateBlocks =
                Objects.requireNonNull(candidateBlocks, "candidateBlocks must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public MatchCandidateBlocks execute(GenerateMatchCandidatesCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        policy.requireAllowed(command.limit());
        SourceRecord sourceRecord = sourceRecords
                .findByIdentity(command.sourceRecordIdentity())
                .orElseThrow(() -> new SourceRecordNotFoundException(command.sourceRecordIdentity()));

        int fetchLimit = command.limit() + 1;
        var partyKeys = new PartyBlockingKeys(required(sourceRecord, "inn"), value(sourceRecord, "ogrn"));
        CandidateBlockPage<PartyCandidateBlock> parties = partyPage(
                candidateBlocks.findParties(partyKeys, fetchLimit), command.limit(), fetchLimit);

        CandidateBlockPage<SiteCandidateBlock> sites = hasSiteContext(sourceRecord)
                ? sitePage(
                        candidateBlocks.findSites(siteKeys(sourceRecord), fetchLimit),
                        command.limit(),
                        fetchLimit)
                : new CandidateBlockPage<>(command.limit(), false, List.of());

        return new MatchCandidateBlocks(command.sourceRecordIdentity(), parties, sites);
    }

    private static CandidateBlockPage<PartyCandidateBlock> partyPage(
            List<PartyCandidateBlock> blocks, int limit, int fetchLimit) {
        requireBounded(blocks, fetchLimit, "party");
        Map<SupplierPartyId, PartyCandidateBlock> unique = new LinkedHashMap<>();
        blocks.forEach(block -> unique.merge(block.partyId(), block, GenerateMatchCandidatesService::merge));
        List<PartyCandidateBlock> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing(block -> block.partyId().value()));
        return page(sorted, limit);
    }

    private static CandidateBlockPage<SiteCandidateBlock> sitePage(
            List<SiteCandidateBlock> blocks, int limit, int fetchLimit) {
        requireBounded(blocks, fetchLimit, "site");
        Map<SupplierSiteId, SiteCandidateBlock> unique = new LinkedHashMap<>();
        blocks.forEach(block -> unique.merge(block.siteId(), block, GenerateMatchCandidatesService::merge));
        List<SiteCandidateBlock> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparing(block -> block.siteId().value()));
        return page(sorted, limit);
    }

    private static PartyCandidateBlock merge(PartyCandidateBlock first, PartyCandidateBlock second) {
        var evidence = EnumSet.copyOf(first.matchedKeys());
        evidence.addAll(second.matchedKeys());
        return new PartyCandidateBlock(first.partyId(), evidence);
    }

    private static SiteCandidateBlock merge(SiteCandidateBlock first, SiteCandidateBlock second) {
        if (!first.partyId().equals(second.partyId())) {
            throw new CandidateBlockingPortException(
                    "candidate blocking returned one site under multiple parties");
        }
        var evidence = EnumSet.copyOf(first.matchedKeys());
        evidence.addAll(second.matchedKeys());
        return new SiteCandidateBlock(first.siteId(), first.partyId(), evidence);
    }

    private static <T> CandidateBlockPage<T> page(List<T> candidates, int limit) {
        boolean truncated = candidates.size() > limit;
        List<T> selected = truncated ? candidates.subList(0, limit) : candidates;
        return new CandidateBlockPage<>(limit, truncated, selected);
    }

    private static void requireBounded(List<?> candidates, int fetchLimit, String entityType) {
        Objects.requireNonNull(candidates, entityType + " candidates must not be null");
        if (candidates.size() > fetchLimit) {
            throw new CandidateBlockingPortException(
                    entityType + " candidate blocking exceeded its fetch limit");
        }
    }

    private static SiteBlockingKeys siteKeys(SourceRecord sourceRecord) {
        return new SiteBlockingKeys(
                required(sourceRecord, "inn"),
                value(sourceRecord, "kpp"),
                value(sourceRecord, "site_code"),
                value(sourceRecord, "procurement_bu_code"),
                value(sourceRecord, "site_purpose"),
                required(sourceRecord, "country_code"),
                value(sourceRecord, "postal_code"),
                value(sourceRecord, "region"),
                required(sourceRecord, "city"),
                required(sourceRecord, "address_line"));
    }

    private static boolean hasSiteContext(SourceRecord sourceRecord) {
        return value(sourceRecord, "kpp") != null
                || value(sourceRecord, "site_code") != null
                || value(sourceRecord, "procurement_bu_code") != null
                || value(sourceRecord, "site_purpose") != null;
    }

    private static String required(SourceRecord sourceRecord, String field) {
        String value = value(sourceRecord, field);
        if (value == null) {
            throw new IllegalArgumentException("canonical source field is required for blocking: " + field);
        }
        return value;
    }

    private static String value(SourceRecord sourceRecord, String field) {
        String value = sourceRecord.canonicalValues().get(field);
        return value == null || value.isBlank() ? null : value;
    }
}
