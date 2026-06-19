package com.innowise.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "gateway.security")
public class SecurityProperties {

    private List<String> openEndpoints = new ArrayList<>();

    public List<String> getOpenEndpoints() {
        return openEndpoints;
    }

    public void setOpenEndpoints(List<String> openEndpoints) {
        this.openEndpoints = openEndpoints;
    }
}
