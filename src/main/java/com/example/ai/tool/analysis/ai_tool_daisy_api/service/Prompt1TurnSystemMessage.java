package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The system message and doc 18 text each turn receives, ported from {@code daisy_preload.py}
 * ({@code sm_for}, {@code trim_s0}, {@code doc18_part}). The SM text itself is never rewritten:
 * whole top-level sections are kept or left out, and in §0 whole lines are left out.
 */
public final class Prompt1TurnSystemMessage {

    /** A top-level SM section: a line of '=', the line "&lt;n&gt;. TITLE", a line of '='. */
    private static final Pattern SECTION = Pattern.compile("^=+\\n(\\d+)\\. [^\\n]*\\n=+\\n", Pattern.MULTILINE);

    private Prompt1TurnSystemMessage() {
    }

    /**
     * The SM cut to the header lines before §0 plus the turn's sections; §0 without the lines that
     * do not apply to this turn.
     *
     * @throws IllegalArgumentException when a §0 line the configuration names is not found exactly once,
     *                                  i.e. the SM changed and the configuration must follow
     */
    public static String forTurn(String systemMessage, String turn, Prompt1TurnConfig config) {
        String text = systemMessage.replace("\r\n", "\n");
        String key = Prompt1TurnConfig.tableKey(turn);
        List<Integer> keep = config.smSections().get(key);
        if (keep == null) {
            throw new IllegalArgumentException("No SM sections configured for turn " + turn);
        }
        List<int[]> marks = new ArrayList<>();
        Matcher m = SECTION.matcher(text);
        while (m.find()) {
            marks.add(new int[]{m.start(), Integer.parseInt(m.group(1))});
        }
        if (marks.isEmpty()) {
            throw new IllegalArgumentException("No top-level sections found in the system message");
        }
        StringBuilder out = new StringBuilder(text.substring(0, marks.get(0)[0]));
        for (int i = 0; i < marks.size(); i++) {
            int number = marks.get(i)[1];
            if (!keep.contains(number)) {
                continue;
            }
            int end = i + 1 < marks.size() ? marks.get(i + 1)[0] : text.length();
            String part = text.substring(marks.get(i)[0], end);
            out.append(number == 0 ? trimSection0(part, key, config) : part);
        }
        return out.toString().stripTrailing() + "\n";
    }

    /**
     * §0 without the lines that start with an S0_DROP or an S0_D_ONLY entry. Since Jan's v04z the
     * backend writes the final JSON, so the D-only lines go to no turn.
     */
    static String trimSection0(String section0, String key, Prompt1TurnConfig config) {
        String[] lines = section0.split("\n", -1);
        List<String> drop = new ArrayList<>(config.s0Drop());
        drop.addAll(config.s0DOnly());
        for (String prefix : drop) {
            long n = java.util.Arrays.stream(lines).filter(line -> line.startsWith(prefix)).count();
            if (n != 1) {
                throw new IllegalArgumentException("SM §0: line '" + prefix + "' found " + n
                        + " times (expected 1); update s0Drop/s0DOnly in the turn configuration");
            }
        }
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            if (drop.stream().noneMatch(prefix -> line.startsWith(prefix))) {
                kept.add(line);
            }
        }
        return String.join("\n", kept);
    }

    /**
     * Only the general writing rules and the turn's '###' sections of doc 18, as the three message
     * parts Jan's backend adds: an empty line, the block title and the text.
     */
    public static List<String> doc18Part(String doc18, String turn, Prompt1TurnConfig config) {
        List<String> headings = new ArrayList<>();
        headings.add(config.doc18General());
        headings.addAll(config.doc18Headings().getOrDefault(Prompt1TurnConfig.tableKey(turn), List.of()));
        List<String> out = new ArrayList<>();
        boolean on = false;
        for (String line : doc18.replace("\r\n", "\n").split("\n", -1)) {
            if (line.startsWith("#")) {
                on = headings.stream().anyMatch(heading -> line.startsWith(heading));
            }
            if (on) {
                out.add(line);
            }
        }
        return List.of("", "=== WORKING-FILE FORMAT (doc 18, parts for this turn) ===", String.join("\n", out).strip());
    }
}
