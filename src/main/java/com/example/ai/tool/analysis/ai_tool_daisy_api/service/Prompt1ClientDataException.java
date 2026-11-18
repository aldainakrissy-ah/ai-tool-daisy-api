package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * The client's data cannot start a Prompt 1 run: no portal client for the email (404), the email matches more than
 * one client, or the intake is not filled in yet (409). The message names no personal data.
 */
@Getter
public class Prompt1ClientDataException extends RuntimeException {

    private final HttpStatus status;

    public Prompt1ClientDataException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
