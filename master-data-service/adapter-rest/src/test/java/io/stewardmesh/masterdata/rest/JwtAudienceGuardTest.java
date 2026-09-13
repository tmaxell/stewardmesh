package io.stewardmesh.masterdata.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JwtAudienceGuardTest {

    @Test
    void acceptsAConfiguredAudience() {
        assertEquals(
                JwtAudienceGuard.class,
                new JwtAudienceGuard(List.of("stewardmesh-master-data")).getClass());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void refusesToStartWhenTheOnlyAudienceIsBlank(String audience) {
        List<String> audiences = List.of(audience);

        var failure = assertThrows(IllegalStateException.class, () -> new JwtAudienceGuard(audiences));

        assertTrue(failure.getMessage().contains("audiences"));
    }

    @Test
    void refusesToStartWhenNoAudienceIsConfiguredAtAll() {
        List<String> none = List.of();

        var failure = assertThrows(IllegalStateException.class, () -> new JwtAudienceGuard(none));

        assertTrue(failure.getMessage().contains("another service"));
    }
}
