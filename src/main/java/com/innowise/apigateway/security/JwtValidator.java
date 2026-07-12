package com.innowise.apigateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtValidator {

    private final SecretKey signingKey;

    public JwtValidator(@Value("${jwt.secret}") String secret) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Claims validateAccessToken(String token) {
        Claims claims = parse(token);
        if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("Token has no expiry");
        }
        if (!"access".equals(claims.get("typ", String.class))) {
            throw new IllegalArgumentException("Token is not an access token");
        }
        return claims;
    }
}
