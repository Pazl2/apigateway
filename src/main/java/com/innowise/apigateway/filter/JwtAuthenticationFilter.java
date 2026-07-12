package com.innowise.apigateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.innowise.apigateway.security.JwtValidator;
import com.innowise.apigateway.config.SecurityProperties;
import com.innowise.apigateway.dto.output.ErrorResponse;
import io.jsonwebtoken.Claims;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtValidator jwtValidator;
    private final SecurityProperties securityProperties;
    private final ObjectMapper objectMapper;
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtValidator jwtValidator,
                                   SecurityProperties securityProperties,
                                   ObjectMapper objectMapper) {
        this.jwtValidator = jwtValidator;
        this.securityProperties = securityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        if (isOpenEndpoint(path)) {
            return chain.filter(exchange);
        }

        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange, "Authorization header with Bearer token is required");
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        Claims claims;
        try {
            claims = jwtValidator.validateAccessToken(token);
        } catch (Exception e) {
            return unauthorized(exchange, "Invalid or expired token");
        }

        Object userId = claims.get("userId");
        Object role = claims.get("role");
        String login = claims.getSubject();

        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> {
                    if (login != null) {
                        headers.set("X-User-Login", login);
                    }
                    if (userId != null) {
                        headers.set("X-User-Id", String.valueOf(userId));
                    }
                    if (role != null) {
                        headers.set("X-User-Role", String.valueOf(role));
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isOpenEndpoint(String path) {
        return securityProperties.openEndpoints().stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        ErrorResponse body = new ErrorResponse(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", message);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (IOException e) {
            return Mono.error(e);
        }
    }

    @Override
    public int getOrder() {
        return -1;
    }
}