package io.stewardmesh.masterdata.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordQuery;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidateQuery;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.identity.MatchExplanationQuery;
import io.stewardmesh.masterdata.application.port.in.GetGoldenRecord;
import io.stewardmesh.masterdata.application.port.in.GetIdentityResolutionStatus;
import io.stewardmesh.masterdata.application.port.in.GetMatchExplanation;
import io.stewardmesh.masterdata.application.port.in.ListIdentityResolutionCandidates;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
public class IdentityResolutionController {

    private final GetIdentityResolutionStatus statuses;
    private final ListIdentityResolutionCandidates candidates;
    private final GetMatchExplanation explanations;
    private final GetGoldenRecord goldenRecords;

    public IdentityResolutionController(
            GetIdentityResolutionStatus statuses,
            ListIdentityResolutionCandidates candidates,
            GetMatchExplanation explanations,
            GetGoldenRecord goldenRecords) {
        this.statuses = statuses;
        this.candidates = candidates;
        this.explanations = explanations;
        this.goldenRecords = goldenRecords;
    }

    @GetMapping("/identity-resolution/sources/{originSystem}/{sourceRecordId}/versions/{sourceVersion}")
    @Operation(summary = "Read identity-resolution status")
    public IdentityResolutionStatusResponse status(
            @PathVariable String originSystem,
            @PathVariable String sourceRecordId,
            @PathVariable String sourceVersion,
            @RequestParam String rulesetId) {
        return IdentityResolutionStatusResponse.from(statuses.execute(
                key(originSystem, sourceRecordId, sourceVersion, rulesetId)));
    }

    @GetMapping("/identity-resolution/sources/{originSystem}/{sourceRecordId}/versions/{sourceVersion}/candidates")
    @Operation(summary = "Read one bounded candidate page")
    public MatchCandidatePageResponse candidates(
            @PathVariable String originSystem,
            @PathVariable String sourceRecordId,
            @PathVariable String sourceVersion,
            @RequestParam String rulesetId,
            @RequestParam String entityType,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        var query = new IdentityResolutionCandidateQuery(
                key(originSystem, sourceRecordId, sourceVersion, rulesetId),
                entityType(entityType), number(page, "page"), number(size, "size"));
        return MatchCandidatePageResponse.from(candidates.execute(query));
    }

    @GetMapping("/identity-resolution/sources/{originSystem}/{sourceRecordId}/versions/{sourceVersion}/candidates/{candidateId}/explanation")
    @Operation(summary = "Read complete non-sensitive candidate evidence")
    public MatchCandidateResponse explanation(
            @PathVariable String originSystem,
            @PathVariable String sourceRecordId,
            @PathVariable String sourceVersion,
            @PathVariable String candidateId,
            @RequestParam String rulesetId,
            @RequestParam String entityType) {
        var query = new MatchExplanationQuery(
                key(originSystem, sourceRecordId, sourceVersion, rulesetId),
                entityType(entityType), uuid(candidateId, "candidateId"));
        return MatchCandidateResponse.from(explanations.execute(query), true);
    }

    @GetMapping("/golden-records/{entityType}/{entityId}")
    @Operation(summary = "Read one current golden-record projection with provenance")
    public GoldenRecordResponse goldenRecord(
            @PathVariable String entityType, @PathVariable String entityId) {
        GoldenEntityType type;
        try {
            type = GoldenEntityType.valueOf(entityType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw request("GOLDEN_ENTITY_TYPE_INVALID", "entityType must be PARTY, ADDRESS, or SITE");
        }
        return GoldenRecordResponse.from(
                goldenRecords.execute(new GoldenRecordQuery(type, uuid(entityId, "entityId"))));
    }

    private static IdentityResolutionKey key(
            String originSystem, String sourceRecordId, String sourceVersion, String rulesetId) {
        try {
            return new IdentityResolutionKey(
                    new SourceRecordIdentity(
                            new SourceSystemRef(originSystem), sourceRecordId,
                            Long.parseLong(sourceVersion)),
                    new MatchRulesetId(rulesetId));
        } catch (IllegalArgumentException exception) {
            throw request("SOURCE_IDENTITY_INVALID", "source identity or ruleset is invalid");
        }
    }

    private static MatchEntityType entityType(String value) {
        try {
            return MatchEntityType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw request("MATCH_ENTITY_TYPE_INVALID", "entityType must be PARTY or SITE");
        }
    }

    private static int number(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw request("PAGINATION_INVALID", name + " must be an integer");
        }
    }

    private static UUID uuid(String value, String name) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw request("ENTITY_ID_INVALID", name + " must be a UUID");
        }
    }

    private static IntakeRequestException request(String code, String detail) {
        return new IntakeRequestException(code, HttpStatus.BAD_REQUEST, detail);
    }
}
