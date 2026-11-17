package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

/**
 * Prompt 1 cannot run: there is no usable release (none active and no local files, or the active release is made for
 * another backend). Not a bad request and not a bug: the API answers 503 with this message, which names only the
 * release and what to do (upload and activate a release for this backend), never file content.
 */
public class Prompt1NotReadyException extends IllegalStateException {

    public Prompt1NotReadyException(String message, Throwable cause) {
        super(message, cause);
    }
}
