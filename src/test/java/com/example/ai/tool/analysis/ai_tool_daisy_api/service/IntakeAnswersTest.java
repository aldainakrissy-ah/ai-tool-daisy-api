package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.phase_two_api.pojo.LocalizedTextDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.OptionDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The numbering and answer rules of the client intake for Prompt 1. */
class IntakeAnswersTest {

    @Test
    void numberIsTheNumberTheTextStartsWith() {
        assertEquals("2.1.2", IntakeAnswers.number("2.1.2 Zo ja, in welke mate?"));
        assertEquals("5.10", IntakeAnswers.number("5.10 Worden jouw emotionele behoeften vaak niet herkend?"));
        assertEquals("4.13", IntakeAnswers.number("4.13 Iets anders"));
        assertEquals("1", IntakeAnswers.number("1. Algemene oriëntatie en welbevinden"));
        assertEquals("2.1", IntakeAnswers.number("2.1 Energie en vermoeidheid"));
        assertNull(IntakeAnswers.number("Heb je klachten?"));
        assertNull(IntakeAnswers.number("2026 was een goed jaar"));  // a bare number is not a question number
        assertNull(IntakeAnswers.number(null));
    }

    @Test
    void splitGivesTheNumberAndTheTextWithoutIt() {
        assertEquals(new IntakeAnswers.Numbered("2.1.2", "Zo ja, in welke mate?"), IntakeAnswers.split("2.1.2 Zo ja, in welke mate?"));
        assertEquals(new IntakeAnswers.Numbered("1", "Algemene oriëntatie en welbevinden"),
                IntakeAnswers.split("1. Algemene oriëntatie en welbevinden"));
        assertEquals(new IntakeAnswers.Numbered(null, "Heb je klachten?"), IntakeAnswers.split("Heb je klachten?"));
    }

    @Test
    void dutchTextFirstThenEnglish() {
        assertEquals("Licht", IntakeAnswers.dutch(new LocalizedTextDto("Mild", "Licht")));
        assertEquals("Mild", IntakeAnswers.dutch(new LocalizedTextDto("Mild", " ")));
        assertNull(IntakeAnswers.dutch(null));
    }

    @Test
    void answersBecomeJansText() {
        QuestionDto yesNo = question("yes-no", List.of());
        QuestionDto scale3 = question("likert-scale-3", List.of(option("mild", "Mild", "Licht"),
                option("moderate", "Moderate", "Matig"), option("severe", "Severe", "Ernstig")));
        QuestionDto score = question("likert-scale-5", List.of(option("0", "0", "0"), option("3", "3", "3"), option("4", "4", "4")));
        QuestionDto text = question("text", List.of());

        assertEquals("Ja", IntakeAnswers.answer(yesNo, response(r -> r.setAnswerBoolean(true))));
        assertEquals("Nee", IntakeAnswers.answer(yesNo, response(r -> r.setAnswerBoolean(false))));
        assertEquals("Ja", IntakeAnswers.answer(yesNo, response(r -> r.setAnswerText("yes"))));
        assertEquals("Matig", IntakeAnswers.answer(scale3, response(r -> r.setAnswerText("moderate"))));
        assertEquals("3", IntakeAnswers.answer(score, response(r -> r.setAnswerText("3"))));
        assertEquals("0", IntakeAnswers.answer(score, response(r -> r.setAnswerText("0"))));
        assertEquals("4", IntakeAnswers.answer(score, response(r -> r.setAnswerNumber(4.0))));
        assertEquals("Mijn eigen woorden", IntakeAnswers.answer(text, response(r -> r.setAnswerText("Mijn eigen woorden"))));
        assertEquals("2.5", IntakeAnswers.answer(text, response(r -> r.setAnswerNumber(2.5))));
        assertEquals("Licht, Ernstig", IntakeAnswers.answer(scale3, response(r -> r.setAnswerJson(List.of("mild", "severe")))));
        assertEquals("{\"a\":\"b\"}", IntakeAnswers.answer(text, response(r -> r.setAnswerJson(Map.of("a", "b")))));
    }

    @Test
    void noAnswerIsNull() {
        QuestionDto text = question("text", List.of());
        assertNull(IntakeAnswers.answer(text, null));
        assertNull(IntakeAnswers.answer(text, response(r -> { })));
        assertNull(IntakeAnswers.answer(text, response(r -> r.setAnswerText("  "))));
        assertNull(IntakeAnswers.answer(text, response(r -> r.setAnswerJson(List.of()))));
        assertNull(IntakeAnswers.answer(text, response(r -> r.setAnswerJson(Map.of()))));
    }

    private static QuestionDto question(String type, List<OptionDto> options) {
        QuestionDto q = new QuestionDto();
        q.setType(type);
        q.setOptions(options);
        return q;
    }

    private static OptionDto option(String value, String en, String nl) {
        OptionDto o = new OptionDto();
        o.setValue(value);
        o.setLabel(new LocalizedTextDto(en, nl));
        return o;
    }

    private static QuestionResponseDto response(java.util.function.Consumer<QuestionResponseDto> fill) {
        QuestionResponseDto r = new QuestionResponseDto();
        fill.accept(r);
        return r;
    }
}
