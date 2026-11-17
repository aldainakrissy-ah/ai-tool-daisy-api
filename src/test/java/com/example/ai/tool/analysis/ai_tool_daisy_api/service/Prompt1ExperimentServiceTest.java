package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.openai.models.ReasoningEffort;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The shared helpers of the Prompt 1 experiment runs: cost, totals, intake and reasoning effort. */
class Prompt1ExperimentServiceTest {

    @Test
    void costPricesUncachedCachedCacheWritesAndOutputSeparately() {
        // a live call of 4 November: nothing cached, the shared prefix written to the cache
        CallUsage call1 = CallUsage.builder().inputTokens(60530).cachedInputTokens(0).cacheWriteTokens(60527L)
                .outputTokens(26330).build();
        assertEquals(0.046728, Prompt1ExperimentService.estimateCostUsd(call1, 0.20, 0.02, 0.25, 1.20), 1e-9);

        CallUsage withoutCacheWrites = CallUsage.builder().inputTokens(1000).cachedInputTokens(400).outputTokens(100).build();
        assertEquals(0.000248, Prompt1ExperimentService.estimateCostUsd(withoutCacheWrites, 0.20, 0.02, 0.25, 1.20), 1e-9);
    }

    @Test
    void totalAddsUpTheCostOfTheCalls() {
        CallUsage total = Prompt1ExperimentService.total(List.of(
                CallUsage.builder().inputTokens(10).estimatedCostUsd(0.046728).build(),
                CallUsage.builder().inputTokens(20).estimatedCostUsd(0.039156).build()));
        assertEquals(30, total.getInputTokens());
        assertEquals(0.085884, total.getEstimatedCostUsd(), 1e-9);
    }

    @Test
    void intakeNeedsAtLeastOneNonEmptyFile() {
        assertThrows(IllegalArgumentException.class, () -> Prompt1ExperimentService.requireIntake(List.of()));
        assertThrows(IllegalArgumentException.class, () -> Prompt1ExperimentService.requireIntake(
                List.of(new IntakeFile("empty.json", " \n".getBytes(StandardCharsets.UTF_8)))));
        assertDoesNotThrow(() -> Prompt1ExperimentService.requireIntake(List.of(
                new IntakeFile("a.json", "{}".getBytes(StandardCharsets.UTF_8)),
                new IntakeFile("b.json", "[]".getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    void aRunNeedsAnIntakeQuestionnaireWithAtLeastOneFilledAnswer() {
        assertThrows(IllegalArgumentException.class, () -> clientData("{\"document_type\":\"nothing\"}"),
                "no questionnaire: refused before any paid call");
        assertThrows(IllegalArgumentException.class, () -> clientData(
                "{\"document_type\":\"intake_questionnaire\",\"structured_data\":{\"q1\":null,\"q2\":\"\"}}"),
                "a questionnaire without filled answers: refused");
        assertDoesNotThrow(() -> clientData(
                "{\"document_type\":\"intake_questionnaire\",\"structured_data\":{\"q1\":\"ja\"}}"));
    }

    private static void clientData(String json) {
        Prompt1TurnInventory.requireClientData(Prompt1TurnInventory.build(
                List.of(new IntakeFile("test.json", json.getBytes(StandardCharsets.UTF_8)))));
    }

    @Test
    void reasoningEffortAcceptsOnlyOpenAiValues() {
        assertEquals(ReasoningEffort.of("high"), Prompt1ExperimentService.parseReasoningEffort(" High "));
        assertEquals(ReasoningEffort.of("xhigh"), Prompt1ExperimentService.parseReasoningEffort(""));
        assertThrows(IllegalArgumentException.class, () -> Prompt1ExperimentService.parseReasoningEffort("max"));
    }
}
