package com.innowise.apigateway.dto.output;

public record ErrorResponse(int status, String error, String message) {
}
