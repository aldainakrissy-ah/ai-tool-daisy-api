package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import java.util.*;

/**
 * Doc 18 TSV helpers: tab-separated, first line the column names, lists comma-separated, an empty
 * value written as "—". Rows are read as plain splits (no quoting), like Jan's server.
 */
public final class Prompt1Tsv {

    public static final String EMPTY = "—";

    private Prompt1Tsv() {
    }

    /** The rows of a TSV text by the value of one column (a later row with the same value wins), in file order. */
    public static Map<String, Map<String, String>> byId(String tsv, String column) {
        Map<String, Map<String, String>> out = new LinkedHashMap<>();
        for (Map<String, String> row : rows(tsv)) {
            out.put(row.get(column), row);
        }
        return out;
    }

    /** FD_016 → FTF_016: a functional teleonic form has the number of its direction (the part after the first '_'). */
    public static String ftfOf(String directionId) {
        return "FTF_" + directionId.substring(directionId.indexOf('_') + 1);
    }

    /** FTF_016 → FD_016. */
    public static String directionOf(String ftfId) {
        return "FD_" + ftfId.substring(ftfId.indexOf('_') + 1);
    }

    /** The rows of a TSV text as column → value maps (missing trailing cells are empty strings). */
    public static List<Map<String, String>> rows(String text) {
        List<Map<String, String>> rows = new ArrayList<>();
        if (text == null) {
            return rows;
        }
        List<String> lines = text.replace("\r\n", "\n").lines().filter(line -> !line.isBlank()).toList();
        if (lines.isEmpty()) {
            return rows;
        }
        String[] head = lines.get(0).split("\t", -1);
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split("\t", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < head.length; i++) {
                row.put(head[i].strip(), i < cells.length ? cells[i] : "");
            }
            rows.add(row);
        }
        return rows;
    }

    /** The IDs in a list cell: comma (or semicolon) separated, without blanks and "—". */
    public static List<String> ids(String cell) {
        List<String> ids = new ArrayList<>();
        if (cell == null) {
            return ids;
        }
        for (String part : cell.replace(';', ',').split(",")) {
            String id = part.strip();
            if (!id.isEmpty() && !EMPTY.equals(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    /** A cell value for writing: "—" when empty, tabs and line breaks replaced by spaces. */
    public static String cell(Object value) {
        String text = value == null ? "" : value.toString();
        if (text.isEmpty()) {
            return EMPTY;
        }
        return text.replace("\t", " ").replace("\r", " ").replace("\n", " ");
    }

    /** A complete TSV text: the header line, one line per row, a closing line break. */
    public static String text(String header, List<List<String>> rows) {
        StringBuilder out = new StringBuilder(header).append('\n');
        for (List<String> row : rows) {
            out.append(String.join("\t", row)).append('\n');
        }
        return out.toString();
    }

    /** The number at the end of an ID, e.g. 16 for FD_016; null when there is none. */
    public static Integer number(String id) {
        if (id == null) {
            return null;
        }
        int end = id.length();
        int start = end;
        while (start > 0 && Character.isDigit(id.charAt(start - 1))) {
            start--;
        }
        return start == end ? null : Integer.valueOf(id.substring(start, end));
    }

    /** All digits of an ID read as one number, 0 when it has none (e.g. 79 for M079, 12 for OBS_012). */
    public static int digits(String id) {
        String d = id == null ? "" : id.replaceAll("\\D", "");
        return d.isEmpty() ? 0 : Integer.parseInt(d);
    }

    /** The values of a list cell split on commas only, without blanks and "—" (Jan's {@code lst}). */
    public static List<String> commaList(String cell) {
        List<String> out = new ArrayList<>();
        if (cell == null) {
            return out;
        }
        for (String part : cell.split(",")) {
            String x = part.strip();
            if (!x.isEmpty() && !EMPTY.equals(x)) {
                out.add(x);
            }
        }
        return out;
    }

    /** The HETA refs of a3_candidates.tsv in its {@code order} column. */
    public static List<String> candidateOrder(String candidates) {
        return rows(candidates).stream()
                .sorted(Comparator.comparingInt(r -> Integer.parseInt(r.get("order").strip())))
                .map(r -> r.get("heta_ref")).toList();
    }

    /** Python's {@code str.rstrip(chars)}: the text without any of these characters at its end. */
    public static String rstrip(String text, String chars) {
        int end = text.length();
        while (end > 0 && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(0, end);
    }

    /** Python's {@code str.strip(chars)}: the text without any of these characters at its start and end. */
    public static String strip(String text, String chars) {
        int start = 0;
        int end = text.length();
        while (start < end && chars.indexOf(text.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && chars.indexOf(text.charAt(end - 1)) >= 0) {
            end--;
        }
        return text.substring(start, end);
    }
}
