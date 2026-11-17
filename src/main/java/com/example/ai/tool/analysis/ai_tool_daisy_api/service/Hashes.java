package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** SHA-256 as lowercase hex, for content hashes of documents, sections, system messages and schemas. */
public final class Hashes {

    private Hashes() {
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** SHA-256 over several parts in order (the same as over their concatenation). */
    public static String sha256(List<byte[]> parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            parts.forEach(part -> digest.update(part));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }
}
