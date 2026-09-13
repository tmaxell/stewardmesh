package io.stewardmesh.masterdata.rest;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;

/**
 * Refuses to start when the resource server would accept a token minted for a different service.
 *
 * <p>Spring Boot installs the audience validator only when
 * {@code spring.security.oauth2.resourceserver.jwt.audiences} carries a value. An empty list
 * silently drops the check instead of reporting it, and the REST and MCP boundaries would then
 * honour any token the issuer signed for anyone. Failing at startup keeps that misconfiguration
 * impossible to deploy unnoticed.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class JwtAudienceGuard {

    JwtAudienceGuard(
            @Value("${spring.security.oauth2.resourceserver.jwt.audiences:}") List<String> audiences) {
        if (audiences.stream().noneMatch(JwtAudienceGuard::isPresent)) {
            throw new IllegalStateException(
                    "spring.security.oauth2.resourceserver.jwt.audiences must name this resource "
                            + "server, otherwise a token issued for another service is accepted");
        }
    }

    private static boolean isPresent(String audience) {
        return audience != null && !audience.isBlank();
    }
}
