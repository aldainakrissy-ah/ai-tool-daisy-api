package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.openai.core.JsonValue;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Shared helpers of the Prompt 1 experiment runs (Jan's turn-based route): the intake files held in
 * memory, reasoning effort, per-call usage and its estimated cost, and hashes. The earlier two-call
 * route that lived here was retired on 13 Nov 2026 (a backup of its code is kept outside the repo).
 */
@Slf4j
@Service
public class Prompt1ExperimentService {

    @Value("${experiment.prompt1.price.input-per-million:0.20}")
    private double priceInputPerMillion;

    @Value("${experiment.prompt1.price.cached-input-per-million:0.02}")
    private double priceCachedInputPerMillion;

    @Value("${experiment.prompt1.price.cache-write-per-million:0.25}")
    private double priceCacheWritePerMillion;

    @Value("${experiment.prompt1.price.output-per-million:1.20}")
    private double priceOutputPerMillion;

    /** A client intake file held in memory, so a background run can use it after the request has ended. */
    public record IntakeFile(String name, byte[] content) {
        public static IntakeFile of(MultipartFile file) throws IOException {
            return new IntakeFile(file.getOriginalFilename(), file.getBytes());
        }
    }

    /** {@code none}, {@code minimal}, {@code low}, {@code medium}, {@code high} or {@code xhigh}. */
    static ReasoningEffort parseReasoningEffort(String value) {
        String effort = value == null || value.isBlank() ? "xhigh" : value.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("none", "minimal", "low", "medium", "high", "xhigh").contains(effort)) {
            throw new IllegalArgumentException("Unknown reasoning effort '" + value
                    + "'; expected none, minimal, low, medium, high or xhigh");
        }
        return ReasoningEffort.of(effort);
    }

    static CallUsage usageOf(String label, Response response, long durationMs) {
        CallUsage.CallUsageBuilder usage = CallUsage.builder()
                .call(label)
                .responseId(response.id())
                .status(response.status().map(status -> status.toString()).orElse(null))
                .durationMs(durationMs);
        response.usage().ifPresent(u -> usage
                .inputTokens(u.inputTokens())
                .cachedInputTokens(u.inputTokensDetails().cachedTokens())
                .cacheWriteTokens(firstLong(u.inputTokensDetails()._additionalProperties(), u._additionalProperties(),
                        "cache_write_tokens"))
                .outputTokens(u.outputTokens())
                .reasoningTokens(u.outputTokensDetails().reasoningTokens()));
        return usage.build();
    }

    /** The call's cost at the experiment.prompt1.price.* settings (used when a model has no price of its own). */
    CallUsage withCost(CallUsage usage) {
        usage.setEstimatedCostUsd(estimateCostUsd(usage, priceInputPerMillion, priceCachedInputPerMillion,
                priceCacheWritePerMillion, priceOutputPerMillion));
        return usage;
    }

    /**
     * Uncached input, cached input, cache writes and output priced separately (USD per million
     * tokens), rounded to six decimals. Without reported cache writes, the uncached input is
     * priced at the normal input rate.
     */
    static double estimateCostUsd(CallUsage usage, double inputPerMillion, double cachedInputPerMillion,
                                  double cacheWritePerMillion, double outputPerMillion) {
        long cacheWrites = usage.getCacheWriteTokens() == null ? 0 : usage.getCacheWriteTokens();
        long uncached = Math.max(0, usage.getInputTokens() - usage.getCachedInputTokens() - cacheWrites);
        double cost = (uncached * inputPerMillion
                + usage.getCachedInputTokens() * cachedInputPerMillion
                + cacheWrites * cacheWritePerMillion
                + usage.getOutputTokens() * outputPerMillion) / 1_000_000d;
        return Math.round(cost * 1_000_000d) / 1_000_000d;
    }

    private static Long firstLong(Map<String, JsonValue> first, Map<String, JsonValue> second, String key) {
        for (Map<String, JsonValue> properties : List.of(first, second)) {
            JsonValue value = properties.get(key);
            if (value != null) {
                try {
                    return value.convert(Long.class);
                } catch (RuntimeException e) {
                    log.debug("Could not read {} from usage: {}", key, e.getMessage());
                }
            }
        }
        return null;
    }

    static CallUsage total(List<CallUsage> calls) {
        CallUsage total = CallUsage.builder().call("total").build();
        long cacheWrites = 0;
        boolean anyCacheWrites = false;
        double cost = 0;
        boolean anyCost = false;
        for (CallUsage call : calls) {
            total.setDurationMs(total.getDurationMs() + call.getDurationMs());
            total.setInputTokens(total.getInputTokens() + call.getInputTokens());
            total.setCachedInputTokens(total.getCachedInputTokens() + call.getCachedInputTokens());
            total.setOutputTokens(total.getOutputTokens() + call.getOutputTokens());
            total.setReasoningTokens(total.getReasoningTokens() + call.getReasoningTokens());
            if (call.getCacheWriteTokens() != null) {
                cacheWrites += call.getCacheWriteTokens();
                anyCacheWrites = true;
            }
            if (call.getEstimatedCostUsd() != null) {
                cost += call.getEstimatedCostUsd();
                anyCost = true;
            }
        }
        total.setCacheWriteTokens(anyCacheWrites ? cacheWrites : null);
        total.setEstimatedCostUsd(anyCost ? Math.round(cost * 1_000_000d) / 1_000_000d : null);
        return total;
    }

    /** Refuses a request without intake files, or with an empty one (IllegalArgumentException). */
    static void requireIntake(List<IntakeFile> files) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("At least one client intake file is required");
        }
        for (IntakeFile file : files) {
            if (new String(file.content(), StandardCharsets.UTF_8).isBlank()) {
                throw new IllegalArgumentException("Intake file is empty: " + file.name());
            }
        }
    }
}
