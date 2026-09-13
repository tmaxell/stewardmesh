package io.stewardmesh.masterdata.bootstrap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves the configured resource server actually decodes and validates a real bearer token.
 *
 * <p>The acceptance proof injects an already-authenticated token so it can focus on the governed
 * lifecycle. That leaves signature, expiry and audience unexercised, which is how a local realm
 * unable to grant any MCP scope stayed unnoticed for a whole stage. This test signs real tokens
 * against a JWK set the application fetches over HTTP, so the production decoder is the thing under
 * test.
 */
@Testcontainers
@SpringBootTest(properties = "stewardmesh.messaging.enabled=false")
@AutoConfigureMockMvc
class McpTokenAuthenticationIT {

    private static final String AUDIENCE = "stewardmesh-master-data";
    private static final String ISSUER = "https://synthetic-issuer.invalid/realms/stewardmesh";
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private static final RSAKey SIGNING_KEY = generateKey("synthetic-signing-key");
    private static final RSAKey FOREIGN_KEY = generateKey("synthetic-foreign-key");
    private static final HttpServer JWK_SERVER = startJwkServer();

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWK_SERVER.getAddress().getPort() + "/jwks");
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
    }

    @AfterAll
    static void stopJwkServer() {
        JWK_SERVER.stop(0);
    }

    @Test
    void acceptsATokenSignedForThisServiceAndRefusesOneMintedForAnother() throws Exception {
        mcpInitialize(token(AUDIENCE, SIGNING_KEY, Instant.now().plusSeconds(300)))
                .andExpect(status().isOk());

        mcpInitialize(token("some-other-service", SIGNING_KEY, Instant.now().plusSeconds(300)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAnExpiredTokenAndOneSignedByAForeignKey() throws Exception {
        mcpInitialize(token(AUDIENCE, SIGNING_KEY, Instant.now().minusSeconds(60)))
                .andExpect(status().isUnauthorized());

        mcpInitialize(token(AUDIENCE, FOREIGN_KEY, Instant.now().plusSeconds(300)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void stillEnforcesScopeOnceARealTokenIsAccepted() throws Exception {
        String importId = UUID.randomUUID().toString();

        mockMvc.perform(get("/api/v1/supplier-imports/{importId}", importId)
                        .header(
                                "Authorization",
                                "Bearer " + token(AUDIENCE, SIGNING_KEY, Instant.now().plusSeconds(300))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/supplier-imports/{importId}", importId)
                        .header(
                                "Authorization",
                                "Bearer "
                                        + token(
                                                AUDIENCE,
                                                SIGNING_KEY,
                                                Instant.now().plusSeconds(300),
                                                "supplier-import.read")))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions mcpInitialize(String token)
            throws Exception {
        String request =
                """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{\
                "protocolVersion":"%s","capabilities":{},\
                "clientInfo":{"name":"synthetic-token-probe","version":"1.0"}}}
                """
                        .formatted(PROTOCOL_VERSION);
        return mockMvc.perform(post("/mcp")
                .header("Authorization", "Bearer " + token)
                .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(request));
    }

    private static String token(String audience, RSAKey key, Instant expiresAt) {
        return token(audience, key, expiresAt, "mdm.supplier.read");
    }

    private static String token(String audience, RSAKey key, Instant expiresAt, String scope) {
        try {
            var claims = new JWTClaimsSet.Builder()
                    .subject("synthetic-caller")
                    .issuer(ISSUER)
                    .audience(audience)
                    .issueTime(Date.from(Instant.now().minusSeconds(30)))
                    .expirationTime(Date.from(expiresAt))
                    .claim("scope", scope)
                    .build();
            var jwt = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .type(JOSEObjectType.JWT)
                            .keyID(key.getKeyID())
                            .build(),
                    claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (Exception failure) {
            throw new IllegalStateException("could not mint a synthetic token", failure);
        }
    }

    private static RSAKey generateKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (Exception failure) {
            throw new IllegalStateException("could not generate a synthetic signing key", failure);
        }
    }

    private static HttpServer startJwkServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] jwks = new JWKSet(List.of(SIGNING_KEY.toPublicJWK()))
                    .toString()
                    .getBytes(StandardCharsets.UTF_8);
            server.createContext("/jwks", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (OutputStream body = exchange.getResponseBody()) {
                    body.write(jwks);
                }
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new IllegalStateException("could not start the synthetic JWK server", failure);
        }
    }
}
