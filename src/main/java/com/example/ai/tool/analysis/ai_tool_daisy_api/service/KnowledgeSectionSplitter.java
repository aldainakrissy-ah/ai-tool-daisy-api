package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rebuilds complete, keyed sections from a document's ordered chunks.
 * <p>
 * Sections follow the document's own structure, never a size limit:
 * <ul>
 *   <li>R3, R4 and R5, and 05, 06 and 07 when they carry KEY lines: a new section starts at every
 *       {@code KEY: <key> | <heading>} line, e.g. {@code R4:22.11:01}, {@code R5:ELEM:22.7} or
 *       {@code 05:HETA-42-BDNF}. An {@code END: <name>} line, or a key ending in {@code :END},
 *       closes the current section; text after it becomes {@code <code>:TRAILER}.</li>
 *   <li>In R3, R4 and R5 a {@code #} heading directly followed by a KEY or END line is dropped,
 *       because the KEY line carries it; a level-2 heading without a KEY starts its own section
 *       {@code <code>:<heading>}, e.g. {@code R3:HETA lookup}.</li>
 *   <li>05 without KEY lines (v5): a new section starts at every HETA module line
 *       ({@code HETA: <name>, HETA-JSON identifier= <id>}), keyed {@code 05:<id>};</li>
 *   <li>07 without KEY lines: a new section starts at every {@code SECTION B<n>} line, keyed
 *       {@code 07:SECTION B<n>};</li>
 *   <li>all other documents: one section per heading path, keyed {@code <code>:<heading path>}.</li>
 * </ul>
 * Text before the first marker becomes the {@code <code>:PREAMBLE} section.
 */
@Component
public class KnowledgeSectionSplitter {

    static final String PREAMBLE = "PREAMBLE";
    static final String TRAILER = "TRAILER";
    private static final String HEADING_SEPARATOR = " > ";
    private static final Set<String> KEYED_REFERENCE_CODES = Set.of("R3", "R4", "R5");
    private static final Set<String> OPTIONALLY_KEYED_CODES = Set.of("05", "06", "07");
    private static final Set<String> HEADING_CODES_WITH_KEYED_PASSAGES = Set.of("01", "02", "03");

    private static final Pattern KEY_LINE = Pattern.compile("^KEY:\\s*(\\S+)\\s*\\|\\s*(.*)$");
    /** {@code KEY: 02:END}, with or without a heading part. */
    private static final Pattern KEY_END_LINE = Pattern.compile("^KEY:\\s*(\\S+:END)\\s*(\\|.*)?$");
    private static final Pattern END_LINE = Pattern.compile("^END:\\s*\\S+");
    private static final Pattern HEADING_LINE = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*$");
    // "ident\\w*" also accepts the "identier" spelling used for SCFA in 05 v5, and the identifier
    // may follow the closing bold: "**HETA: Prostaglandins, HETA-JSON identifier =** HETA-41-PROS"
    private static final Pattern HETA_MODULE_LINE =
            Pattern.compile("^\\**\\s*(HETA:\\s*.+?[,.]\\s*HETA-JSON ident\\w*\\s*=\\s*\\**\\s*([A-Za-z0-9-]+))\\s*\\**\\s*$");
    /** A module line whose identifier is on the next line, e.g. "**HETA: BDNF (...). HETA-JSON identifier =**". */
    private static final Pattern HETA_MODULE_LINE_WITHOUT_ID =
            Pattern.compile("^(\\**\\s*HETA:\\s*.+?[,.]\\s*HETA-JSON ident\\w*\\s*=)\\s*\\**\\s*$");
    private static final Pattern HETA_IDENTIFIER = Pattern.compile("^\\s*(HETA-\\d+-[A-Za-z0-9]+)\\s*$");
    private static final Pattern QUESTIONNAIRE_SECTION_LINE =
            Pattern.compile("^\\**\\s*(SECTION\\s+(B\\d+)\\b.*?)\\s*\\**\\s*$");

    public record SourceChunk(int ordinal, String headingPath, String body) {
    }

    public record ParsedSection(String key, String heading, int seq, String content) {
    }

    public record SplitResult(List<ParsedSection> sections, List<String> duplicateKeys) {
    }

    /** A line that starts a new section, with the section's key and heading. */
    private record Marker(String key, String heading) {
    }

    public SplitResult split(String documentCode, List<SourceChunk> chunks) {
        if (KEYED_REFERENCE_CODES.contains(documentCode)) {
            return splitByKeys(documentCode, bodyLines(chunks), true);
        }
        if (OPTIONALLY_KEYED_CODES.contains(documentCode)) {
            List<String> lines = bodyLines(chunks);
            if (lines.stream().anyMatch(line -> KEY_LINE.matcher(line).matches())) {
                return splitByKeys(documentCode, lines, false);
            }
        }
        if (HEADING_CODES_WITH_KEYED_PASSAGES.contains(documentCode)) {
            List<String> lines = bodyLines(chunks);
            if (lines.stream().anyMatch(line -> KEY_LINE.matcher(line).matches())) {
                return splitKeyedPassagesAndHeadings(documentCode, lines);
            }
        }
        return switch (documentCode) {
            case "05" -> splitByMarkers(documentCode, joinSplitModuleLines(bodyLines(chunks)), line -> {
                Matcher m = HETA_MODULE_LINE.matcher(line.trim());
                return m.matches() ? new Marker("05:" + m.group(2), m.group(1).replace("*", "")) : null;
            });
            case "07" -> splitByMarkers(documentCode, bodyLines(chunks), line -> {
                Matcher m = QUESTIONNAIRE_SECTION_LINE.matcher(line.trim());
                return m.matches() ? new Marker("07:SECTION " + m.group(2), m.group(1)) : null;
            });
            default -> splitByHeadings(documentCode, chunks);
        };
    }

    private static List<String> bodyLines(List<SourceChunk> chunks) {
        List<String> lines = new ArrayList<>();
        for (SourceChunk chunk : chunks) {
            lines.addAll(Arrays.asList(chunk.body().split("\n", -1)));
        }
        return lines;
    }

    /** Puts a HETA module identifier written on the following line back onto its module line. */
    private static List<String> joinSplitModuleLines(List<String> lines) {
        List<String> joined = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            Matcher module = HETA_MODULE_LINE_WITHOUT_ID.matcher(lines.get(i).trim());
            Matcher identifier = i + 1 < lines.size() ? HETA_IDENTIFIER.matcher(lines.get(i + 1)) : null;
            if (module.matches() && identifier != null && identifier.matches()) {
                joined.add(module.group(1) + " " + identifier.group(1) + "**");
                i++;
            } else {
                joined.add(lines.get(i));
            }
        }
        return joined;
    }

    private SplitResult splitByMarkers(String documentCode, List<String> documentLines,
                                       java.util.function.Function<String, Marker> markerOf) {
        SectionCollector collector = new SectionCollector();
        String key = documentCode + ":" + PREAMBLE;
        String heading = PREAMBLE;
        List<String> lines = new ArrayList<>();

        for (String line : documentLines) {
            Marker marker = markerOf.apply(line);
            if (marker == null) {
                lines.add(line);
                continue;
            }
            collector.add(key, heading, lines, PREAMBLE.equals(heading));
            key = marker.key();
            heading = marker.heading();
            lines = new ArrayList<>();
        }
        collector.add(key, heading, lines, PREAMBLE.equals(heading));
        return collector.result();
    }

    /**
     * Splits at KEY lines and closes a section at END lines. With {@code referenceHeadings}, a
     * heading right before a KEY or END line is dropped and a level-2 heading without a KEY
     * starts a named section; deeper headings stay in the current section's content.
     */
    private SplitResult splitByKeys(String documentCode, List<String> documentLines, boolean referenceHeadings) {
        SectionCollector collector = new SectionCollector();
        String key = documentCode + ":" + PREAMBLE;
        String heading = PREAMBLE;
        boolean skipIfEmpty = true;
        List<String> lines = new ArrayList<>();

        for (int i = 0; i < documentLines.size(); i++) {
            String line = documentLines.get(i);
            Matcher keyLine = KEY_LINE.matcher(line);
            boolean endLine = END_LINE.matcher(line).lookingAt() || KEY_END_LINE.matcher(line).matches();
            if (keyLine.matches() || endLine) {
                collector.add(key, heading, lines, skipIfEmpty);
                lines = new ArrayList<>();
                if (endLine || keyLine.group(1).endsWith(":END")) {
                    key = documentCode + ":" + TRAILER;
                    heading = TRAILER;
                    skipIfEmpty = true;
                } else {
                    key = keyLine.group(1);
                    heading = keyLine.group(2).trim();
                    skipIfEmpty = false;
                }
                continue;
            }
            Matcher headingLine = HEADING_LINE.matcher(line);
            if (referenceHeadings && headingLine.matches()) {
                String next = nextNonBlank(documentLines, i + 1);
                if (next != null && (KEY_LINE.matcher(next).matches() || END_LINE.matcher(next).lookingAt()
                        || KEY_END_LINE.matcher(next).matches())) {
                    continue;
                }
                if (headingLine.group(1).length() == 2) {
                    collector.add(key, heading, lines, skipIfEmpty);
                    lines = new ArrayList<>();
                    heading = headingLine.group(2).replaceAll("\\s+", " ");
                    key = documentCode + ":" + heading;
                    skipIfEmpty = false;
                    continue;
                }
            }
            lines.add(line);
        }
        collector.add(key, heading, lines, skipIfEmpty);
        return collector.result();
    }

    /**
     * Documents 01–03 from v4 / v04 / v05 on: each passage the system message names is a keyed span
     * ({@code KEY: 02:B.112 | …} up to {@code KEY: 02:END}) and becomes a section with that key,
     * subheadings included as content. Text outside keyed spans keeps one section per heading path;
     * the heading stack runs through the keyed spans, so paths stay complete.
     */
    private SplitResult splitKeyedPassagesAndHeadings(String documentCode, List<String> documentLines) {
        SectionCollector collector = new SectionCollector();
        Deque<String> path = new ArrayDeque<>();
        Deque<Integer> levels = new ArrayDeque<>();
        String headingPath = "";
        List<String> headingLines = new ArrayList<>();
        boolean continuation = false;
        String key = null;
        String keyHeading = null;
        List<String> keyLines = new ArrayList<>();

        for (String line : documentLines) {
            Matcher keyEnd = KEY_END_LINE.matcher(line);
            Matcher keyLine = KEY_LINE.matcher(line);
            Matcher headingLine = HEADING_LINE.matcher(line);
            boolean isHeading = headingLine.matches() && !headingLine.group(2).isEmpty();

            if (keyEnd.matches()) {
                if (key != null) {
                    collector.add(key, keyHeading, keyLines, false);
                    key = null;
                    // text after the span, before the next heading, continues the current heading path
                    continuation = true;
                }
                continue;
            }
            if (keyLine.matches()) {
                if (key != null) {
                    collector.add(key, keyHeading, keyLines, false);
                } else {
                    addHeadingSection(collector, documentCode, headingPath, headingLines, continuation);
                }
                headingLines = new ArrayList<>();
                key = keyLine.group(1);
                keyHeading = keyLine.group(2).trim();
                keyLines = new ArrayList<>();
                continue;
            }
            if (isHeading) {
                int level = headingLine.group(1).length();
                while (!levels.isEmpty() && levels.peekLast() >= level) {
                    levels.removeLast();
                    path.removeLast();
                }
                levels.addLast(level);
                path.addLast(headingLine.group(2).replaceAll("\\s+", " "));
                String newPath = String.join(HEADING_SEPARATOR, path);
                if (key == null) {
                    // a repeated heading with the same path continues that section, as in splitByHeadings
                    if (!newPath.equals(headingPath)) {
                        addHeadingSection(collector, documentCode, headingPath, headingLines, continuation);
                        headingLines = new ArrayList<>();
                        continuation = false;
                        headingPath = newPath;
                    }
                    continue;
                }
                headingPath = newPath;
                continuation = true;
            }
            if (key != null) {
                keyLines.add(line);
            } else {
                headingLines.add(line);
            }
        }
        if (key != null) {
            collector.add(key, keyHeading, keyLines, false);
        } else {
            addHeadingSection(collector, documentCode, headingPath, headingLines, continuation);
        }
        return collector.result();
    }

    /** A heading section; a preamble or a continuation after a keyed span is skipped when blank. */
    private static void addHeadingSection(SectionCollector collector, String documentCode, String headingPath,
                                          List<String> lines, boolean continuation) {
        collector.add(headingKey(documentCode, headingPath), lastHeading(headingPath), lines,
                continuation || headingPath.isEmpty());
    }

    private static String nextNonBlank(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (!lines.get(i).isBlank()) {
                return lines.get(i);
            }
        }
        return null;
    }

    private SplitResult splitByHeadings(String documentCode, List<SourceChunk> chunks) {
        SectionCollector collector = new SectionCollector();
        String currentPath = null;
        List<String> lines = new ArrayList<>();

        for (SourceChunk chunk : chunks) {
            String path = chunk.headingPath() == null ? "" : chunk.headingPath().trim();
            if (currentPath != null && !path.equals(currentPath)) {
                collector.add(headingKey(documentCode, currentPath), lastHeading(currentPath), lines, false);
                lines = new ArrayList<>();
            }
            currentPath = path;
            lines.addAll(Arrays.asList(chunk.body().split("\n", -1)));
        }
        if (currentPath != null) {
            collector.add(headingKey(documentCode, currentPath), lastHeading(currentPath), lines, false);
        }
        return collector.result();
    }

    private static String headingKey(String documentCode, String headingPath) {
        return documentCode + ":" + (headingPath.isEmpty() ? PREAMBLE : headingPath);
    }

    private static String lastHeading(String headingPath) {
        if (headingPath.isEmpty()) {
            return PREAMBLE;
        }
        int index = headingPath.lastIndexOf(HEADING_SEPARATOR);
        return index < 0 ? headingPath : headingPath.substring(index + HEADING_SEPARATOR.length());
    }

    /** Collects sections in order, numbering repeated keys and skipping an empty preamble. */
    private static final class SectionCollector {
        private final List<ParsedSection> sections = new ArrayList<>();
        private final Map<String, Integer> keyCounts = new HashMap<>();
        private final List<String> duplicateKeys = new ArrayList<>();

        void add(String key, String heading, List<String> lines, boolean skipIfEmpty) {
            String content = trimBlankLines(lines);
            if (skipIfEmpty && content.isEmpty()) {
                return;
            }
            int count = keyCounts.getOrDefault(key, 0) + 1;
            keyCounts.put(key, count);
            String uniqueKey = key;
            if (count > 1) {
                uniqueKey = key + "#" + count;
                if (count == 2) {
                    duplicateKeys.add(key);
                }
            }
            sections.add(new ParsedSection(uniqueKey, heading, sections.size() + 1, content));
        }

        SplitResult result() {
            return new SplitResult(sections, duplicateKeys);
        }

        private static String trimBlankLines(List<String> lines) {
            int start = 0;
            int end = lines.size();
            while (start < end && lines.get(start).isBlank()) {
                start++;
            }
            while (end > start && lines.get(end - 1).isBlank()) {
                end--;
            }
            return String.join("\n", lines.subList(start, end));
        }
    }
}
