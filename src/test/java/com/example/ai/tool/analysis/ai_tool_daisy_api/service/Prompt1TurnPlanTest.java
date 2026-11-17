package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Model and effort per turn: presets, settings, per-run choice and the cost at each model's prices. */
class Prompt1TurnPlanTest {

    private Prompt1TurnRunService service;

    @BeforeEach
    void setUp() {
        service = new Prompt1TurnRunService(null, mock(Prompt1SourcePackageService.class), mock(Prompt1RunFiles.class),
                new ObjectMapper());
        ReflectionTestUtils.setField(service, "model", "gpt-6.1-terra");
        ReflectionTestUtils.setField(service, "effort", "A1=high,A2=high,rest=medium");
    }

    @Test
    void withoutPresetTheModelAndEffortSettingsApply() {
        Prompt1TurnRunService.Plan plan = service.plan(null);
        assertNull(plan.name());
        assertEquals("gpt-6.1-terra", plan.models().get("C"));
        assertEquals("high", plan.efforts().get("A1"));
        assertEquals("medium", plan.efforts().get("D"));

        ReflectionTestUtils.setField(service, "turnModels", "A1=gpt-6.1-terra,rest=gpt-6-luna");
        assertEquals("gpt-6-luna", service.plan(null).models().get("A3"));
        assertEquals("gpt-6.1-terra", service.plan(null).models().get("A1"));
    }

    @Test
    void presetsPutTheStrongModelAndEffortWhereTheJudgementIs() {
        Prompt1TurnRunService.Plan economy = service.plan("economy");
        assertEquals(Map.of("A1", "gpt-6.1-terra", "A2", "gpt-6.1-terra", "A3", "gpt-6.1-terra", "B", "gpt-6-luna",
                "C", "gpt-6-luna", "D", "gpt-6-luna"), economy.models());
        assertEquals("medium", economy.efforts().get("A3"));
        assertEquals("low", economy.efforts().get("D"));

        Prompt1TurnRunService.Plan quality = service.plan("Quality");
        assertEquals("quality", quality.name());
        assertEquals("xhigh", quality.efforts().get("A1"));
        assertEquals("xhigh", quality.efforts().get("A2"));
        assertEquals("gpt-6.1-terra", quality.models().get("C"));

        ReflectionTestUtils.setField(service, "preset", "economy");
        assertEquals("economy", service.plan(null).name(), "the configured preset applies when a run asks for none");
        assertEquals("standard", service.plan("standard").name(), "a run's own preset wins");
        assertThrows(IllegalArgumentException.class, () -> service.plan("cheap"));
    }

    @Test
    void turnPlansRejectUnknownTurnsAndMissingModels() {
        assertThrows(IllegalArgumentException.class, () -> Prompt1TurnRunService.modelPlan("A1=gpt-6.1-terra"));
        assertThrows(IllegalArgumentException.class, () -> Prompt1TurnRunService.modelPlan("E=gpt-6-luna,rest=x"));
        assertThrows(IllegalArgumentException.class, () -> Prompt1TurnRunService.effortPlan("A1=maximum"));
    }

    @Test
    void eachCallIsPricedAtItsOwnModel() {
        CallUsage luna = CallUsage.builder().inputTokens(10_000).cachedInputTokens(4_000).outputTokens(2_000).build();
        CallUsage terra = CallUsage.builder().inputTokens(10_000).cachedInputTokens(4_000).outputTokens(2_000).build();

        Prompt1OpenAiCaller caller = new Prompt1OpenAiCaller(null, mock(Prompt1ExperimentService.class));
        ReflectionTestUtils.setField(caller, "prices", "gpt-6.1-terra=1.00/0.05/1.25/5.00,gpt-6-luna=0.10/0.01/0.125/0.50");
        assertEquals(0.00164, caller.withModelCost("gpt-6-luna", luna).getEstimatedCostUsd(), 1e-9);
        assertEquals(0.0162, caller.withModelCost("gpt-6.1-terra", terra).getEstimatedCostUsd(), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> Prompt1OpenAiCaller.modelPrices("gpt-6-luna=0.10/0.01"));
    }
}
