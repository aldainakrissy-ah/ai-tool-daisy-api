package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.phase_two_api.pojo.LocalizedTextDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.OptionDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionDto;
import com.example.ai.tool.analysis.phase_two_api.pojo.QuestionResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The rules that turn a saved questionnaire answer into the text of Jan's intake form, in one place:
 * <ul>
 *   <li>the question or section number is the number its text starts with ("5.8 …", "2.1 …", "1. …"); question IDs
 *       are never used for this, since they differ between servers;</li>
 *   <li>an answer becomes text: yes/no as "Ja"/"Nee", a chosen option as its Dutch label (so a 0–4 score stays the
 *       digit), a list as its labels joined, anything else as it is.</li>
 * </ul>
 */
final class IntakeAnswers {

    /** "5.10 …", "2.1.2 …" or "1. …"; a bare number ("2026 …") is not a question number. */
    private static final Pattern NUMBERED = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)+|\\d+(?=\\.))\\.?\\s+(.*)$", Pattern.DOTALL);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** A text split into its leading number (null when it has none) and the rest (the whole text then). */
    record Numbered(String number, String text) {
    }

    private IntakeAnswers() {
    }

    /** "5.10 Worden …" → (5.10, "Worden …"); "1. Algemene …" → (1, "Algemene …"); "Heb je …" → (null, "Heb je …"). */
    static Numbered split(String text) {
        Matcher m = text == null ? null : NUMBERED.matcher(text);
        return m != null && m.matches() ? new Numbered(m.group(1), m.group(2).strip()) : new Numbered(null, text);
    }

    /** The number a text starts with; null when it has none. */
    static String number(String text) {
        return split(text).number();
    }

    /** The Dutch text, or the English one when there is no Dutch text; null when there is neither. */
    static String dutch(LocalizedTextDto text) {
        if (text == null) {
            return null;
        }
        return text.getNl() != null && !text.getNl().isBlank() ? text.getNl() : text.getEn();
    }

    /**
     * The answer as text, read in the same order as the intake app reads it back (text, number, yes/no, list);
     * null when the question has no answer.
     */
    static String answer(QuestionDto question, QuestionResponseDto response) {
        if (response == null) {
            return null;
        }
        String value;
        if (response.getAnswerText() != null && !response.getAnswerText().isBlank()) {
            value = choice(question, response.getAnswerText());
        } else if (response.getAnswerNumber() != null) {
            value = choice(question, plainNumber(response.getAnswerNumber()));
        } else if (response.getAnswerBoolean() != null) {
            value = response.getAnswerBoolean() ? "Ja" : "Nee";
        } else {
            value = json(question, response.getAnswerJson());
        }
        return value == null || value.isBlank() ? null : value;
    }

    /** A chosen value as its Dutch option label; yes/no as Ja/Nee; any other text as it is. */
    private static String choice(QuestionDto question, String value) {
        String v = value.strip();
        if (question != null && "yes-no".equals(question.getType())) {
            String lower = v.toLowerCase(Locale.ROOT);
            if (lower.equals("yes") || lower.equals("true")) {
                return "Ja";
            }
            if (lower.equals("no") || lower.equals("false")) {
                return "Nee";
            }
        }
        List<OptionDto> options = question == null ? null : question.getOptions();
        if (options != null) {
            for (OptionDto option : options) {
                if (v.equals(option.getValue())) {
                    String label = dutch(option.getLabel());
                    return label == null || label.isBlank() ? value : label;
                }
            }
        }
        return value;
    }

    private static String json(QuestionDto question, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> list) {
            return list.stream()
                    .filter(item -> item != null)
                    .map(item -> item instanceof String s ? choice(question, s) : json(question, item))
                    .filter(item -> item != null && !item.isBlank())
                    .collect(Collectors.joining(", "));
        }
        if (value instanceof String s) {
            return choice(question, s);
        }
        if (value instanceof Number n) {
            return choice(question, plainNumber(n.doubleValue()));
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    /** 3.0 → "3", 2.5 → "2.5". */
    private static String plainNumber(double number) {
        return BigDecimal.valueOf(number).stripTrailingZeros().toPlainString();
    }
}
