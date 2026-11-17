package com.example.ai.tool.analysis.ai_tool_daisy_api.pojo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * A release of the release store as the API shows it: its state, the upload check and its files by name,
 * role, version, hash and size. Never the file content (the files are confidential).
 *
 * @param status  DRAFT, ACTIVE or RETIRED; REJECTED for an upload that failed a check (nothing was stored)
 * @param passed  true when every check passed
 */
public record ReleaseReport(String id, String prompt, String name, String status, boolean passed,
                            LocalDateTime createdAt, LocalDateTime activatedAt, String changes,
                            Map<String, String> documentVersions, List<Check> checks, List<FileInfo> files) {

    /** One check of the upload; {@code detail} names what is wrong, never file content. */
    public record Check(String name, boolean ok, String detail) {
    }

    public record FileInfo(String file, String role, String documentCode, String version, String sha256, long bytes) {
    }

    /** The same report without the check details and files, for a list. */
    public ReleaseReport summary() {
        return new ReleaseReport(id, prompt, name, status, passed, createdAt, activatedAt, changes, documentVersions,
                null, null);
    }
}
