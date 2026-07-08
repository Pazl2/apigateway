package com.innowise.apigateway.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class PaymentServiceRoutingIntegrationTest {

    private static final String TEST_SECRET = "test-secret-key-please-use-a-longer-value-32-bytes-min";

    private static WireMockServer wireMockServer;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("PAYMENT_SERVICE_URI", () -> "http://localhost:" + wireMockServer.port());
    }

    @Test
    void paymentsRoute_withoutAuthHeader_isRejectedAndNeverReachesPaymentService() {
        webTestClient.get().uri("/api/payments/123")
                .exchange()
                .expectStatus().isUnauthorized();

        wireMockServer.verify(0, getRequestedFor(urlPathEqualTo("/api/payments/123")));
    }

    @Test
    void paymentsRoute_withValidToken_isForwardedToPaymentServiceWithUserHeaders() {
        wireMockServer.stubFor(get(urlPathEqualTo("/api/payments/123"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":123}")));

        String token = generateAccessToken(42L, "USER", "jane.doe");

        webTestClient.get().uri("/api/payments/123")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        wireMockServer.verify(getRequestedFor(urlPathEqualTo("/api/payments/123"))
                .withHeader("X-User-Id", equalTo("42"))
                .withHeader("X-User-Role", equalTo("USER"))
                .withHeader("X-User-Login", equalTo("jane.doe")));
    }

    private String generateAccessToken(Long userId, String role, String login) {
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .subject(login)
                .claim("userId", userId)
                .claim("role", role)
                .claim("typ", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();
    }
}