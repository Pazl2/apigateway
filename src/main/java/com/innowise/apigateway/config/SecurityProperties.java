package com.innowise.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "gateway.security")
public record SecurityProperties(@DefaultValue List<String> openEndpoints) {
}