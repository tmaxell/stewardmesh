package io.stewardmesh.masterdata.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRulesetId;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoldenRecordMetadataEntityTest {

    private static final Instant PROJECTED_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final SurvivorshipRulesetId RULESET =
            new SurvivorshipRulesetId("supplier-survivorship-v1");

    @Test
    void advancesOneProjectionVersionAtATimeWithoutChangingOwnership() {
        UUID partyId = UUID.randomUUID();
        var entity = new GoldenRecordMetadataEntity(
                GoldenEntityType.PARTY,
                partyId,
                partyId,
                null,
                new GoldenRecordVersion(1),
                RULESET,
                PROJECTED_AT);

        assertDoesNotThrow(() -> entity.advance(
                partyId, null, new GoldenRecordVersion(2), RULESET, PROJECTED_AT.plusSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> entity.advance(
                        partyId,
                        null,
                        new GoldenRecordVersion(4),
                        RULESET,
                        PROJECTED_AT.plusSeconds(2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> entity.advance(
                        UUID.randomUUID(),
                        null,
                        new GoldenRecordVersion(3),
                        RULESET,
                        PROJECTED_AT.plusSeconds(2)));
    }

    @Test
    void mapsTheJpaOptimisticLockColumn() throws NoSuchFieldException {
        assertNotNull(GoldenRecordMetadataEntity.class
                .getDeclaredField("lockVersion")
                .getAnnotation(Version.class));
    }

    @Test
    void requiresNewMetadataToStartAtVersionOne() {
        UUID partyId = UUID.randomUUID();

        assertThrows(
                IllegalArgumentException.class,
                () -> new GoldenRecordMetadataEntity(
                        GoldenEntityType.PARTY,
                        partyId,
                        partyId,
                        null,
                        new GoldenRecordVersion(2),
                        RULESET,
                        PROJECTED_AT));
    }
}
