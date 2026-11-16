package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a turn's answer into working files, ported from Jan's {@code daisy_preload.py}
 * ({@code ingest}, {@code ingest_c}, {@code check_c}, {@code clean_c_values}, {@code fill_signals},
 * the A1 repairs, {@code fill_evidence_copies}, {@code set_routes}, {@code join_broken_lines}). Repairs change only form
 * or copies, never a judgement; a real problem refuses the answer with a message the runner sends
 * back to the model for correction.
 */
public final class Prompt1TurnAnswers {

    /** The answer cannot be accepted; the message tells the model what to correct. */
    public static class RefusedException extends RuntimeException {
        public RefusedException(String message) {
            super(message);
        }
    }

    /** One file to write: replace its content, or append to it. */
    public record FileWrite(String name, String content, boolean append) {
    }

    /** The files an accepted answer writes, the steps it closes, the repairs made, and (turn D) the final JSON. */
    public record Ingested(List<FileWrite> files, String closeSteps, List<String> notes, JsonNode output) {
    }

    private static final Pattern BLOCK = Pattern.compile("```\\s*([\\w.]+)\\s*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern SIGNAL_ID = Pattern.compile("S\\d+");
    private static final List<String> QUALIFICATION_CATEGORIES = List.of("polarity", "frequency", "duration_or_onset",
            "situation_or_trigger", "intensity", "uncertainty", "comparison", "actor_or_target", "manner",
            "purpose_or_outcome", "other_source_qualification");
    private static final Map<String, List<String>> C_VALUES = new LinkedHashMap<>();

    static {
        C_VALUES.put("kind", List.of("contribution", "signal"));
        C_VALUES.put("applicable", List.of("yes", "no"));
        C_VALUES.put("route", List.of("functional_correspondence", "canonical_signal"));
        C_VALUES.put("state", List.of("S", "PART", "NE", "C"));
        C_VALUES.put("classification", List.of("direct", "indirect", "not_eligible"));
        C_VALUES.put("decision_route", List.of("functional_correspondence", "canonical_signal", "none"));
    }

    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private Prompt1TurnAnswers() {
    }

    /** The fenced blocks of an answer: file name → body; a later block of the same name replaces an earlier one. */
    public static Map<String, String> blocks(String answer) {
        Map<String, String> blocks = new LinkedHashMap<>();
        Matcher m = BLOCK.matcher(answer);
        while (m.find()) {
            blocks.put(m.group(1), m.group(2));
        }
        return blocks;
    }

    // ---- turns A1, A2, A3 ------------------------------------------------------------------------------------

    /**
     * Checks and repairs the answer of turn A1, A2 or A3. Turns B and D have no model call since
     * Jan's v04z/v06 (see {@link #ingestD}).
     *
     * @param files the run's working files so far
     */
    public static Ingested ingest(Prompt1TurnConfig config, String turn, String answer, Map<String, String> files) {
        Prompt1TurnConfig.Turn table = config.turns().get(turn);
        if (table == null || table.files().isEmpty()) {
            throw new IllegalArgumentException("Turn " + turn + " has no model answer to ingest");
        }
        Map<String, String> blocks = blocks(answer);
        List<String> missing = table.files().stream().filter(name -> !blocks.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new RefusedException("answer lacks block(s): " + String.join(", ", missing));
        }
        List<String> notes = new ArrayList<>();
        joinBrokenLines(config, blocks, table.files(), notes);
        if (turn.equals("A1")) {
            repairA1(blocks, files.get("observations.tsv"), notes);
        }
        List<FileWrite> writes = new ArrayList<>();
        for (String name : table.files()) {
            String body = stripNewlines(blocks.get(name));
            checkColumns(config, name, body);
            writes.add(new FileWrite(name, body + "\n", false));
        }
        if (turn.equals("A1")) {
            Set<String> contributions = admitted(blocks.get("meanings.tsv"));
            List<String> bad = new ArrayList<>();
            for (Map<String, String> d : Prompt1Tsv.rows(blocks.get("directions.tsv"))) {
                for (String id : d.getOrDefault("context_ids", "").replace(" ", "").split(",")) {
                    if (contributions.contains(id)) {
                        bad.add(d.get("direction_id") + ": " + id);
                    }
                }
            }
            if (!bad.isEmpty()) {
                throw new RefusedException("directions.tsv: functional contribution(s) in context_ids (doc 18 allows only "
                        + "retained_context there; move to member_ids of its own group or drop): " + String.join("; ", bad));
            }
            List<String> unconnected = unconnectedDominantContext(blocks);
            if (!unconnected.isEmpty()) {
                throw new RefusedException("directions.tsv/priority.tsv: context of a dominant direction without an "
                        + "established connection (no link in links.tsv to a member of the direction, and not in the "
                        + "observation of a member): " + String.join("; ", unconnected) + ". SM Step 2 "
                        + "(priority_context_meaning_ids) and Step 8: context counts only with the connection the source "
                        + "states; a shared topic is never a ground. Remove the meaning from context_ids and "
                        + "context_meaning_ids of that direction, or record the source link that joins it.");
            }
            checkDominance(blocks.get("cts_admission.tsv"), blocks.get("priority.tsv"));
        }
        if (turn.equals("A2")) {
            Set<String> passed = new HashSet<>();
            for (Map<String, String> row : Prompt1Tsv.rows(blocks.get("ie_core_filter.tsv"))) {
                if ("yes".equals(row.getOrDefault("passes", "").strip())) {
                    passed.add(row.get("ftf_id"));
                }
            }
            List<String> lost = Prompt1TurnRules.dominantFtfs(file(files, "priority.tsv")).stream()
                    .filter(ftf -> !passed.contains(ftf)).toList();
            if (!lost.isEmpty()) {
                throw new RefusedException("ie_core_filter.tsv: dominant " + String.join(", ", lost) + " passes the core "
                        + "filter for no IE. SM Step 4 2a (DOMINANT COVERAGE): reassess it against the E rows of every IE "
                        + "with the doubt rule (in doubt, it passes), record passes = yes for the IE(s) that carry it most "
                        + "closely, and record their cells (6.1C).");
            }
        }
        if (turn.equals("A3")) {  // Step 5 must close with exactly one primary IE (7.2D), or every later turn fails
            try {
                Prompt1TurnRules.selection(blocks.get("a1_selection.tsv"));
            } catch (IllegalStateException e) {
                throw new RefusedException("a1_selection.tsv: " + e.getMessage().replaceFirst("^a1_selection\\.tsv: ", "")
                        + ". Write the row 7.2D | <ie_ref> | primary for the selected IE (SM Step 5; with no "
                        + "primary-eligible IE apply 7.1 PARTIAL-FIT PRIMARY), and one 7.3 row for every other candidate IE.");
            }
        }
        return new Ingested(writes, table.closeSteps(), notes, null);
    }

    // ---- turn D, by the backend --------------------------------------------------------------------------

    /**
     * Turn D since v06: the answer (b2_needs.tsv and b2_coverage.tsv) is written by the backend from
     * the B2 matrix. The ingest numbers the GAPs (15.1), checks the columns and assembles the final
     * JSON from the closed working files (Steps 11, 12, 14, 15.3, 15.4).
     *
     * @param key returns the text of a source key, or null when it is not found
     */
    public static Ingested ingestD(Prompt1TurnConfig config, String answer, Map<String, String> files, String runId,
                                   String runDate, java.util.function.Function<String, String> key) {
        Prompt1TurnConfig.Turn table = config.turns().get("D");
        Map<String, String> blocks = blocks(answer);
        List<String> missing = table.files().stream().filter(name -> !blocks.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("turn D answer lacks block(s): " + String.join(", ", missing));
        }
        List<String> notes = new ArrayList<>();
        Prompt1TurnOutput.B2 numbered = Prompt1TurnOutput.numberGaps(config, file(files, "a3_candidates.tsv"),
                file(files, "heta_canon.tsv"), stripNewlines(blocks.get("b2_needs.tsv")),
                stripNewlines(blocks.get("b2_coverage.tsv")));
        blocks.put("b2_needs.tsv", numbered.needs());
        blocks.put("b2_coverage.tsv", numbered.coverage());
        if (!numbered.notes().isEmpty()) {
            notes.add("GAP-nummers gelijkgemaakt aan de volgorde van 15.1: " + String.join(", ", numbered.notes()));
        }
        List<FileWrite> writes = new ArrayList<>();
        Map<String, String> written = new HashMap<>();
        for (String name : table.files()) {
            String body = stripNewlines(blocks.get(name));
            try {
                checkColumns(config, name, body);
            } catch (RefusedException e) {
                throw new IllegalStateException(e.getMessage());
            }
            writes.add(new FileWrite(name, body + "\n", false));
            written.put(name, body + "\n");
        }
        Prompt1TurnOutput.Output output = Prompt1TurnOutput.assembleOutput(config,
                name -> written.containsKey(name) ? written.get(name) : files.getOrDefault(name, ""), runId, runDate, key);
        writes.add(new FileWrite("output.json", json(output.json()) + "\n", false));
        notes.addAll(output.notes());
        notes.add("eind-JSON door de backend samengesteld (B1 " + output.json().path("b1").size() + ", B2 "
                + output.json().path("b2").path("items").size() + " vragenlijst(en), B3 " + output.json().path("b3").size()
                + " module(s))");
        return new Ingested(writes, table.closeSteps(), notes, output.json());
    }

    // ---- repair turn (v05) ----------------------------------------------------------------------------------

    /** The row key of each file a repair answer may patch (Jan's {@code REPAIR_KEYS}). */
    static final Map<String, List<String>> REPAIR_KEYS = new LinkedHashMap<>();

    static {
        REPAIR_KEYS.put("meanings.tsv", List.of("meaning_id"));
        REPAIR_KEYS.put("links.tsv", List.of("link_id"));
        REPAIR_KEYS.put("grouping.tsv", List.of("direction_id", "meaning_id"));
        REPAIR_KEYS.put("directions.tsv", List.of("direction_id"));
        REPAIR_KEYS.put("cts_admission.tsv", List.of("direction_id"));
        REPAIR_KEYS.put("priority.tsv", List.of("direction_id"));
        REPAIR_KEYS.put("ie_core_filter.tsv", List.of("ie_ref", "ftf_id"));
        REPAIR_KEYS.put("ie_ftf_cells.tsv", List.of("ie_ref", "ftf_id", "element_id"));
    }

    /** The refused answer with the repair rows applied, and what changed per file. */
    public record Merged(String answer, List<String> notes) {
    }

    /**
     * v05: applies the rows of a repair answer to the blocks of the refused answer. A row replaces the row
     * with the same key, a new key adds a row (a new ie_ftf_cells row gets the next seq), and a row whose
     * first cell after the key is DELETE removes that row (Jan's {@code merge_patch}).
     */
    public static Merged mergePatch(String answer, String patch) {
        Map<String, String> blocks = blocks(answer);
        List<String> notes = new ArrayList<>();
        Matcher m = BLOCK.matcher(patch);
        while (m.find()) {
            String name = m.group(1);
            if (!blocks.containsKey(name) || !REPAIR_KEYS.containsKey(name)) {
                continue;
            }
            List<String> lines = stripNewlines(blocks.get(name)).lines().toList();
            if (lines.isEmpty()) {
                continue;  // an empty block cannot be patched; the full correction follows
            }
            List<String> head = List.of(lines.get(0).split("\t", -1));
            List<Integer> keyIndex = REPAIR_KEYS.get(name).stream().map(k -> head.indexOf(k)).toList();
            if (keyIndex.contains(-1)) {
                continue;  // the refused block has no valid header (a key column is missing): leave it to the correction
            }
            int maxKey = keyIndex.stream().mapToInt(i -> i).max().orElse(0);
            int seq = head.indexOf("seq");
            List<String[]> rows = new ArrayList<>();
            lines.subList(1, lines.size()).stream().filter(l -> !l.isBlank()).forEach(l -> rows.add(l.split("\t", -1)));
            Map<List<String>, Integer> position = new HashMap<>();
            for (int j = 0; j < rows.size(); j++) {
                String[] r = rows.get(j);
                if (r.length == head.size()) {
                    position.put(keyIndex.stream().map(i -> r[i]).toList(), j);
                }
            }
            int added = 0;
            int changed = 0;
            int deleted = 0;
            for (String line : stripNewlines(m.group(2)).lines().toList()) {
                String[] c = Arrays.stream(line.split("\t", -1)).map(x -> x.strip()).toArray(String[]::new);
                if (line.isBlank() || Arrays.asList(c).equals(head) || c.length <= maxKey) {
                    continue;
                }
                List<String> key = keyIndex.stream().map(i -> c[i]).toList();
                if (c.length > maxKey + 1 && "DELETE".equals(c[maxKey + 1])) {
                    if (position.containsKey(key) && rows.get(position.get(key)) != null) {
                        rows.set(position.get(key), null);
                        deleted++;
                    }
                    continue;
                }
                if (c.length != head.size()) {
                    continue;
                }
                if (position.containsKey(key) && rows.get(position.get(key)) != null) {
                    if (seq >= 0) {
                        c[seq] = rows.get(position.get(key))[seq];
                    }
                    rows.set(position.get(key), c);
                    changed++;
                } else {
                    if (seq >= 0) {
                        int next = rows.stream().filter(r -> r != null && r[seq].matches("\\d+"))
                                .mapToInt(r -> Integer.parseInt(r[seq])).max().orElse(0) + 1;
                        c[seq] = String.valueOf(next);
                    }
                    rows.add(c);
                    position.put(key, rows.size() - 1);
                    added++;
                }
            }
            List<String> out = new ArrayList<>(List.of(lines.get(0)));
            rows.stream().filter(r -> r != null).forEach(r -> out.add(String.join("\t", r)));
            blocks.put(name, String.join("\n", out));
            if (changed + added + deleted > 0) {  // a block whose rows all failed to apply is no usable repair
                notes.add(name + ": " + changed + " gewijzigd, " + added + " toegevoegd, " + deleted + " verwijderd");
            }
        }
        List<String> text = new ArrayList<>();
        blocks.forEach((name, body) -> text.add("```" + name + "\n" + body.stripTrailing() + "\n```"));
        return new Merged(String.join("\n\n", text), notes);
    }

    /**
     * v05d: for each dominant direction (priority.tsv), every meaning in its context_ids (directions.tsv)
     * or context_meaning_ids (priority.tsv) must be joined to a member of that direction by a link, or
     * sit in the observation of a member. Returns "FD_x: Mnnn" for each one that is not.
     */
    static List<String> unconnectedDominantContext(Map<String, String> blocks) {
        Map<String, Map<String, String>> meanings = new HashMap<>();
        Prompt1Tsv.rows(blocks.get("meanings.tsv")).forEach(r -> meanings.put(r.get("meaning_id"), r));
        Map<String, Map<String, String>> directions = new HashMap<>();
        Prompt1Tsv.rows(blocks.get("directions.tsv")).forEach(r -> directions.put(r.get("direction_id"), r));
        List<Map<String, String>> links = Prompt1Tsv.rows(blocks.get("links.tsv"));
        List<String> bad = new ArrayList<>();
        for (Map<String, String> p : Prompt1Tsv.rows(blocks.get("priority.tsv"))) {
            if (!"dominant".equals(p.getOrDefault("priority_status", "").strip()) || !directions.containsKey(p.get("direction_id"))) {
                continue;
            }
            Map<String, String> d = directions.get(p.get("direction_id"));
            Set<String> members = new HashSet<>(Prompt1Tsv.ids(d.get("member_ids")));
            Set<String> observations = new HashSet<>();
            members.stream().filter(m -> meanings.containsKey(m))
                    .forEach(m -> observations.add(meanings.get(m).get("observation_id")));
            LinkedHashSet<String> context = new LinkedHashSet<>(Prompt1Tsv.ids(d.get("context_ids")));
            context.addAll(Prompt1Tsv.ids(p.get("context_meaning_ids")));
            for (String m : context) {
                if (members.contains(m) || observations.contains(meanings.getOrDefault(m, Map.of()).get("observation_id"))) {
                    continue;
                }
                boolean linked = links.stream().anyMatch(l -> {
                    String from = l.get("from_meaning_id");
                    String to = l.get("to_meaning_id");
                    return (m.equals(from) && members.contains(to)) || (m.equals(to) && members.contains(from));
                });
                if (!linked) {
                    bad.add(p.get("direction_id") + ": " + m);
                }
            }
        }
        return bad;
    }

    /**
     * The two Step 2 errors of Jan's validate_run.py (v0.131): no dominant direction at all, or every
     * admitted direction dominant (with at least two). Either leaves Step 5 without a sound primary IE.
     */
    static void checkDominance(String admission, String priority) {
        Set<String> admitted = new LinkedHashSet<>();
        for (Map<String, String> row : Prompt1Tsv.rows(admission)) {
            if ("admitted".equals(row.getOrDefault("decision", "").strip())) {
                admitted.add(row.get("direction_id"));
            }
        }
        Set<String> dominant = new LinkedHashSet<>();
        for (Map<String, String> row : Prompt1Tsv.rows(priority)) {
            if ("dominant".equals(row.getOrDefault("priority_status", "").strip())) {
                dominant.add(row.get("direction_id"));
            }
        }
        if (dominant.isEmpty()) {
            throw new RefusedException("priority.tsv: no dominant direction (SM Step 2: stap 2 geen dominante richting). "
                    + "Compare the admitted directions again on the grounds G1–G4 and mark the highest position dominant; "
                    + "check in cts_admission.tsv that every functioning direction with a tension is admitted (3.7A).");
        }
        if (admitted.size() >= 2 && dominant.containsAll(admitted)) {
            throw new RefusedException("priority.tsv: all " + admitted.size() + " admitted directions are dominant (SM Step 2); "
                    + "compare them again on the grounds G1–G4: dominant is the highest position only.");
        }
    }

    /** The message for derive_ie_candidates missing rows after turn A2 (Step 5 cannot run without them). */
    public static RefusedException membershipMissing(List<String> missing) {
        return new RefusedException("derive_ie_candidates reports missing cells in ie_ftf_cells.tsv (one row is required "
                + "for every required row E, Q, C of each alternative in which a passing FTF supports an element): "
                + String.join(", ", missing.subList(0, Math.min(60, missing.size()))));
    }

    private static void checkColumns(Prompt1TurnConfig config, String name, String body) {
        String header = config.headers().get(name);
        List<String> lines = body.lines().toList();
        if (lines.isEmpty() || !lines.get(0).equals(header)) {
            throw new RefusedException(name + ": header differs from doc 18:\n  got      "
                    + (lines.isEmpty() ? "" : lines.get(0)) + "\n  expected " + header);
        }
        int columns = header.split("\t", -1).length;
        for (String line : lines.subList(1, lines.size())) {
            int n = line.split("\t", -1).length;
            if (!line.isBlank() && n != columns) {
                throw new RefusedException(name + ": " + n + " columns instead of " + columns + ": "
                        + line.substring(0, Math.min(80, line.length())));
            }
        }
    }

    // ---- turn C ----------------------------------------------------------------------------------------------

    /**
     * Checks and repairs the answer of turn C for one HETA: values cleaned of stray characters, the
     * route set from the canonical_id (v04z2), signal rows filled from the fixed list, rows of this
     * HETA only, one decision row, the 12.4 projection of the decision (v04z), and the rules of
     * Steps 9–10.
     *
     * @param signals  the HETA's fixed signal list (R3 .5.2), empty when it has none
     * @param hetaName the HETA name from KEY R3:&lt;heta&gt;.2, or "" when it cannot be read
     */
    public static Ingested ingestC(Prompt1TurnConfig config, String heta, String answer, Map<String, String> files,
                                   List<String> signals, String hetaName) {
        Map<String, String> blocks = blocks(answer);
        List<String> missing = config.cFiles().stream().filter(name -> !blocks.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new RefusedException("answer lacks block(s): " + String.join(", ", missing));
        }
        List<FileWrite> writes = new ArrayList<>();
        List<String> cleaned = new ArrayList<>();
        Map<String, List<String>> kept = new LinkedHashMap<>();
        joinBrokenLines(config, blocks, config.cFiles(), cleaned);
        for (String name : config.cFiles()) {
            String header = config.headers().get(name);
            List<String> rows = new ArrayList<>();
            for (String line : blocks.get(name).split("\n", -1)) {
                if (line.isBlank()) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (String cell : line.split("\t", -1)) {
                    cells.add(Prompt1Tsv.strip(cell.strip(), "​﻿ ").strip());
                }
                rows.add(String.join("\t", cells));
            }
            rows = cleanValues(config, name, rows, cleaned);
            if (name.equals("heta_cells.tsv")) {
                rows = setRoutes(config, rows, cleaned);
            }
            if (name.equals("heta_canon.tsv")) {
                List<String> problems = new ArrayList<>();
                rows = fillSignals(config, heta, rows, signals, problems);
                if (!problems.isEmpty()) {
                    throw new RefusedException(numbered(problems));
                }
            }
            if (!rows.isEmpty() && rows.get(0).equals(header)) {
                rows = rows.subList(1, rows.size());
            }
            int columns = header.split("\t", -1).length;
            for (String row : rows) {
                String[] cells = row.split("\t", -1);
                if (cells.length != columns) {
                    throw new RefusedException(name + ": " + cells.length + " columns instead of " + columns + ": "
                            + row.substring(0, Math.min(80, row.length())));
                }
                if (!cells[0].equals(heta)) {
                    throw new RefusedException(name + ": row for " + cells[0] + " instead of " + heta);
                }
            }
            if (name.equals("a3_decisions.tsv") && rows.size() != 1) {
                throw new RefusedException("a3_decisions.tsv: " + rows.size() + " rows instead of 1");
            }
            kept.put(name, new ArrayList<>(rows));
        }
        // v04z: heta_name, classification, decision_route and used_indications are the 12.4 projection of the cells
        Prompt1TurnOutput.Decision projected = Prompt1TurnOutput.projectDecision(heta, hetaName,
                asRows(config, "heta_canon.tsv", kept), asRows(config, "heta_cells.tsv", kept),
                asRows(config, "a3_decisions.tsv", kept).get(0));
        kept.put("a3_decisions.tsv", List.of(String.join("\t", config.headerColumns("a3_decisions.tsv").stream()
                .map(column -> projected.row().getOrDefault(column, "")).toList())));
        for (String name : config.cFiles()) {
            String content = (files.containsKey(name) ? "" : config.headers().get(name) + "\n")
                    + String.join("\n", kept.get(name)) + "\n";
            writes.add(new FileWrite(name, content, true));
        }
        List<String> problems = checkC(heta, asRows(config, "heta_canon.tsv", kept),
                asRows(config, "heta_cells.tsv", kept), asRows(config, "a3_decisions.tsv", kept).stream().findFirst().orElse(null));
        if (!problems.isEmpty()) {
            throw new RefusedException(numbered(problems));
        }
        List<String> notes = new ArrayList<>();
        if (!cleaned.isEmpty()) {
            notes.add("vaste waarde opgeschoond: " + String.join("; ", cleaned));
        }
        if (!projected.notes().isEmpty()) {
            notes.add("12.4-projectie wijkt af van het antwoord (backend gevolgd): " + String.join("; ", projected.notes()));
        }
        return new Ingested(writes, "", notes, null);
    }

    /**
     * v04z2: in heta_cells.tsv the route follows from the canonical_id: C1, C2, … (own contribution)
     * → functional_correspondence, S1, S2, … (signal) → canonical_signal. A typo in the route then
     * costs no correction.
     */
    static List<String> setRoutes(Prompt1TurnConfig config, List<String> rows, List<String> notes) {
        String header = config.headers().get("heta_cells.tsv");
        List<String> head = config.headerColumns("heta_cells.tsv");
        int ci = head.indexOf("canonical_id");
        int ri = head.indexOf("route");
        List<String> out = new ArrayList<>();
        for (String line : rows) {
            String[] cells = line.split("\t", -1);
            if (!line.equals(header) && cells.length == head.size()) {
                String want = cells[ci].matches("C\\d+") ? "functional_correspondence"
                        : cells[ci].matches("S\\d+") ? "canonical_signal" : null;
                if (want != null && !cells[ri].equals(want)) {
                    notes.add("heta_cells.tsv " + cells[ci] + " route: '" + cells[ri] + "' → " + want);
                    cells[ri] = want;
                }
            }
            out.add(String.join("\t", cells));
        }
        return out;
    }

    /**
     * v05a: a stray line break inside a cell splits one row in two. A line is a continuation when it
     * has fewer columns than the header and its first cell does not have the form of the block's row
     * keys (the letter/digit pattern of the first cell of the complete rows, e.g. M079, FD_017,
     * 22.25); it is joined to the line before it without adding anything.
     */
    static void joinBrokenLines(Prompt1TurnConfig config, Map<String, String> blocks, List<String> names,
                                List<String> notes) {
        for (String name : names) {
            String header = config.headers().get(name);
            String body = blocks.get(name);
            if (header == null || body == null) {
                continue;
            }
            int columns = header.split("\t", -1).length;
            List<String> lines = stripNewlines(body).lines().toList();
            Set<String> shapes = new HashSet<>();
            for (String line : lines.subList(Math.min(1, lines.size()), lines.size())) {
                String[] cells = line.split("\t", -1);
                if (cells.length >= columns) {
                    shapes.add(shape(cells[0]));
                }
            }
            if (shapes.isEmpty()) {
                continue;
            }
            List<String> out = new ArrayList<>();
            List<String> joined = new ArrayList<>();
            for (String line : lines) {
                String[] cells = line.split("\t", -1);
                String last = out.isEmpty() ? null : out.get(out.size() - 1);
                boolean continuation = last != null && !line.isBlank() && cells.length < columns
                        && !shapes.contains(shape(cells[0])) && out.size() > 1
                        && last.split("\t", -1).length < columns + 3 && !last.equals(lines.get(0));
                if (continuation) {
                    out.set(out.size() - 1, last + line);
                    String key = out.get(out.size() - 1).split("\t", -1)[0];
                    if (!joined.contains(key)) {
                        joined.add(key);
                    }
                } else {
                    out.add(line);
                }
            }
            if (!joined.isEmpty()) {
                blocks.put(name, String.join("\n", out) + "\n");
                notes.add(name + ": regel die door een losse regelovergang was gesplitst weer samengevoegd: "
                        + String.join(", ", joined));
            }
        }
    }

    /** The letter/digit pattern of a row key: runs of letters → A, runs of digits → 9 (M079 → A9, FD_017 → A_9, 22.25 → 9.9). */
    private static String shape(String cell) {
        return cell.strip().replaceAll("[A-Za-z]+", "A").replaceAll("[0-9]+", "9");
    }

    private static String numbered(List<String> problems) {
        StringBuilder out = new StringBuilder("the HETA check found these problems in your files:");
        for (int i = 0; i < problems.size(); i++) {
            out.append('\n').append(i + 1).append(". ").append(problems.get(i));
        }
        return out.toString();
    }

    private static List<Map<String, String>> asRows(Prompt1TurnConfig config, String name, Map<String, List<String>> kept) {
        List<String> head = config.headerColumns(name);
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : kept.get(name)) {
            String[] cells = line.split("\t", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < head.size() && i < cells.length; i++) {
                row.put(head.get(i), cells[i]);
            }
            rows.add(row);
        }
        return rows;
    }

    /** A value with a fixed list that holds a valid value plus stray characters keeps only that value. */
    static List<String> cleanValues(Prompt1TurnConfig config, String name, List<String> rows, List<String> notes) {
        String header = config.headers().get(name);
        List<String> head = config.headerColumns(name);
        List<String> out = new ArrayList<>();
        for (String line : rows) {
            String[] cells = line.split("\t", -1);
            if (!line.equals(header) && cells.length == head.size()) {
                for (Map.Entry<String, List<String>> values : C_VALUES.entrySet()) {
                    int i = head.indexOf(values.getKey());
                    if (i < 0) {
                        continue;
                    }
                    String bare = cells[i].replaceAll("[^A-Za-z0-9_]", "");
                    if (!values.getValue().contains(cells[i]) && values.getValue().contains(bare)) {
                        notes.add(name + " " + cells[1] + " " + values.getKey() + ": '" + cells[i] + "' → " + bare);
                        cells[i] = bare;
                    }
                }
            }
            out.add(String.join("\t", cells));
        }
        return out;
    }

    /** The signal rows get kind, canonical_key and excerpt from the fixed list; exactly one row per listed signal. */
    static List<String> fillSignals(Prompt1TurnConfig config, String heta, List<String> rows, List<String> signals,
                                    List<String> problems) {
        if (signals.isEmpty()) {
            return rows;
        }
        String header = config.headers().get("heta_canon.tsv");
        List<String> head = config.headerColumns("heta_canon.tsv");
        int id = head.indexOf("canonical_id");
        int kind = head.indexOf("kind");
        int key = head.indexOf("canonical_key");
        int excerpt = head.indexOf("excerpt");
        List<String> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String line : rows) {
            String[] cells = line.split("\t", -1);
            if (!line.equals(header) && cells.length == head.size() && SIGNAL_ID.matcher(cells[id]).matches()) {
                int n = Integer.parseInt(cells[id].substring(1));
                if (n >= 1 && n <= signals.size()) {
                    cells[kind] = "signal";
                    cells[key] = "R3:" + heta + ".5.2";
                    cells[excerpt] = signals.get(n - 1);
                }
                seen.add(cells[id]);
            }
            out.add(String.join("\t", cells));
        }
        List<String> want = new ArrayList<>();
        for (int i = 1; i <= signals.size(); i++) {
            want.add("S" + i);
        }
        List<String> sorted = new ArrayList<>(seen);
        sorted.sort(Comparator.comparingInt(s -> Integer.parseInt(s.substring(1))));
        if (!sorted.equals(want)) {
            List<String> extra = seen.stream().filter(s -> !want.contains(s)).toList();
            List<String> lacking = want.stream().filter(s -> !seen.contains(s)).toList();
            List<String> twice = seen.stream().filter(s -> Collections.frequency(seen, s) > 1).distinct().sorted().toList();
            problems.add("heta_canon.tsv: the signal rows must be exactly S1–S" + signals.size()
                    + " (one per listed signal), each once"
                    + (lacking.isEmpty() ? "" : "; missing " + String.join(", ", lacking))
                    + (extra.isEmpty() ? "" : "; not on the list " + String.join(", ", extra))
                    + (twice.isEmpty() ? "" : "; twice " + String.join(", ", twice)));
        }
        return out;
    }

    /** The rules of Steps 9–10 for one HETA (Jan's {@code check_c}); returns the problems. */
    static List<String> checkC(String heta, List<Map<String, String>> canon, List<Map<String, String>> cells,
                               Map<String, String> decision) {
        List<String> bad = new ArrayList<>();
        if (canon.stream().noneMatch(c -> "contribution".equals(c.get("kind")))) {
            bad.add("heta_canon.tsv: " + heta + " has no row with kind = contribution (12.1)");
        }
        Map<String, String> need = new LinkedHashMap<>();
        for (Map<String, String> c : canon) {
            String kind = c.get("kind");
            if (!"contribution".equals(kind) && !"signal".equals(kind)) {
                bad.add("heta_canon.tsv: " + c.get("canonical_id") + " kind '" + kind + "' (allowed: contribution, signal)");
                continue;
            }
            if (!"yes".equals(c.get("applicable")) && !"no".equals(c.get("applicable"))) {
                bad.add("heta_canon.tsv: " + c.get("canonical_id") + " applicable '" + c.get("applicable") + "' (allowed: yes, no)");
            }
            if ("contribution".equals(kind) || "yes".equals(c.get("applicable"))) {
                need.put(c.get("canonical_id"), "contribution".equals(kind) ? "functional_correspondence" : "canonical_signal");
            }
        }
        Map<String, Integer> got = new LinkedHashMap<>();
        for (Map<String, String> x : cells) {
            String route = x.get("route");
            String state = x.get("state");
            if (!"functional_correspondence".equals(route) && !"canonical_signal".equals(route)) {
                bad.add("heta_cells.tsv: " + x.get("canonical_id") + " route '" + route
                        + "' (allowed: functional_correspondence, canonical_signal)");
            }
            if (!List.of("S", "PART", "NE", "C").contains(state)) {
                bad.add("heta_cells.tsv: " + x.get("canonical_id") + " state '" + state + "' (allowed: S, PART, NE, C)");
            }
            if (("S".equals(state) || "PART".equals(state)) && Prompt1Tsv.ids(x.get("meaning_ids")).isEmpty()) {
                bad.add("heta_cells.tsv: " + x.get("canonical_id") + " " + state + " without meaning_ids");
            }
            String gap = x.getOrDefault("gap", "").strip();
            if ("PART".equals(state) && (gap.isEmpty() || Prompt1Tsv.EMPTY.equals(gap))) {
                bad.add("heta_cells.tsv: " + x.get("canonical_id") + " PART without gap");
            }
            got.merge(x.get("canonical_id") + "\u0000" + route, 1, (a, b) -> a + b);
        }
        need.forEach((id, route) -> {
            int n = got.getOrDefault(id + "\u0000" + route, 0);
            if (n != 1) {
                bad.add("heta_cells.tsv: " + id + " × " + route + ": " + n + " rows (exactly 1 required)");
            }
        });
        for (String pair : got.keySet()) {
            String[] parts = pair.split("\u0000", -1);
            if (!parts[1].equals(need.get(parts[0]))) {
                bad.add("heta_cells.tsv: row " + parts[0] + " × " + parts[1] + " has no matching heta_canon.tsv row "
                        + "(contribution, or signal with applicable = yes)");
            }
        }
        if (bad.isEmpty() && decision != null) {
            Set<String> applicable = new HashSet<>();
            canon.stream().filter(c -> "yes".equals(c.get("applicable"))).forEach(c -> applicable.add(c.get("canonical_id")));
            boolean cS = cells.stream().anyMatch(x -> "S".equals(x.get("state")) && "functional_correspondence".equals(x.get("route")));
            boolean cP = cells.stream().anyMatch(x -> "PART".equals(x.get("state")) && "functional_correspondence".equals(x.get("route")));
            boolean sS = cells.stream().anyMatch(x -> "S".equals(x.get("state")) && "canonical_signal".equals(x.get("route"))
                    && applicable.contains(x.get("canonical_id")));
            String classification = cS ? "direct" : (cP || sS) ? "indirect" : "not_eligible";
            String route = cS || cP ? "functional_correspondence" : sS ? "canonical_signal" : "none";
            if (!classification.equals(decision.get("classification"))) {
                bad.add("a3_decisions.tsv: classification '" + decision.get("classification")
                        + "' does not follow from heta_cells.tsv (" + classification + ", 12.4)");
            }
            if (!route.equals(decision.get("decision_route"))) {
                bad.add("a3_decisions.tsv: decision_route '" + decision.get("decision_route")
                        + "' does not follow from heta_cells.tsv (" + route + ", 12.4)");
            }
        }
        return bad;
    }

    // ---- A1 repairs (form only, no judgement) ------------------------------------------------------------

    private static void repairA1(Map<String, String> blocks, String observations, List<String> notes) {
        quietly(() -> notes.addAll(repairA1Format(blocks, observations == null ? null : observationTexts(observations))));
        quietly(() -> {
            List<String> fixed = new ArrayList<>();
            blocks.put("directions.tsv", syncMembers(blocks.get("meanings.tsv"), blocks.get("grouping.tsv"),
                    blocks.get("directions.tsv"), fixed));
            if (!fixed.isEmpty()) {
                notes.add("member_ids overgenomen uit grouping.tsv: " + String.join(", ", fixed));
            }
        });
        quietly(() -> {
            List<String> dropped = new ArrayList<>();
            blocks.put("priority.tsv", trimPriorityBasis(blocks.get("directions.tsv"), blocks.get("priority.tsv"), dropped));
            if (!dropped.isEmpty()) {
                notes.add("niet-dominante richting: betekenis van een andere richting uit basis_meaning_ids gehaald: "
                        + String.join("; ", dropped));
            }
        });
        quietly(() -> {
            List<String> moved = new ArrayList<>();
            blocks.put("directions.tsv", dropContributionContext(blocks.get("meanings.tsv"), blocks.get("directions.tsv"), moved));
            if (!moved.isEmpty()) {
                notes.add("functionele bijdrage uit context_ids gehaald: " + String.join("; ", moved));
            }
        });
        if (observations != null) {
            quietly(() -> {
                List<String> added = new ArrayList<>();
                blocks.put("meanings.tsv", addNullMeanings(observations, blocks.get("meanings.tsv"), added));
                if (!added.isEmpty()) {
                    notes.add("betekenissen van " + added.size() + " lege intakevelden door de backend geschreven: "
                            + String.join(", ", added));
                }
            });
        }
    }

    /** observation_id → the text a meaning's excerpt must occur in (for a question: its answer). */
    static Map<String, String> observationTexts(String observations) {
        Map<String, String> out = new HashMap<>();
        for (Map<String, String> o : Prompt1Tsv.rows(observations)) {
            String value = o.getOrDefault("source_value", "");
            if ("question_response".equals(o.get("record_type"))) {
                try {
                    JsonNode answer = MAPPER.readTree(value).get("answer");
                    if (answer != null) {
                        value = answer.isTextual() ? answer.textValue() : answer.isNull() ? "None" : answer.toString();
                    }
                } catch (JsonProcessingException e) {
                    // keep the raw value
                }
            }
            out.put(o.get("observation_id"), value);
        }
        return out;
    }

    /** An excerpt found in its source only with other capitals becomes the exact source wording. */
    static String fixCase(String excerpt, List<String> texts) {
        if (excerpt == null || excerpt.isEmpty() || Prompt1Tsv.EMPTY.equals(excerpt)
                || texts.stream().anyMatch(text -> text.contains(excerpt))) {
            return excerpt;
        }
        for (String text : texts) {
            String lower = text.toLowerCase(Locale.ROOT);
            int i = lower.indexOf(excerpt.toLowerCase(Locale.ROOT));
            if (i >= 0 && lower.length() == text.length()) {
                return text.substring(i, i + excerpt.length());
            }
        }
        return excerpt;
    }

    /**
     * Jan's {@code repair_a1_format}: a qualification written as 'category&lt;TAB&gt;wording' is joined
     * with '='; a bare category is dropped; an excerpt that differs from its source only in capitals
     * takes the source wording; a link from a meaning to itself is dropped unless cited elsewhere.
     */
    static List<String> repairA1Format(Map<String, String> blocks, Map<String, String> observationTexts) {
        List<String> notes = new ArrayList<>();
        Pattern bare = Pattern.compile("^(?:" + String.join("|", QUALIFICATION_CATEGORIES) + ")$");
        List<String> lines = new ArrayList<>(stripNewlines(blocks.get("meanings.tsv")).lines().toList());
        List<String> head = List.of(lines.get(0).split("\t", -1));
        if (head.contains("qualifications")) {
            int q = head.indexOf("qualifications");
            List<String> joined = new ArrayList<>();
            List<String> dropped = new ArrayList<>();
            for (int n = 1; n < lines.size(); n++) {
                List<String> cells = new ArrayList<>(List.of(lines.get(n).split("\t", -1)));
                int extra = cells.size() - head.size();
                if (extra >= 1) {
                    String merged = joinQualificationColumns(cells.subList(q, q + extra + 1), bare);
                    if (merged != null) {
                        cells.subList(q + 1, q + extra + 1).clear();
                        cells.set(q, merged);
                        joined.add(cells.get(0));
                    }
                }
                if (cells.size() == head.size()) {
                    List<String> parts = Arrays.stream(cells.get(q).split(";;", -1)).map(p -> p.strip()).toList();
                    List<String> keep = parts.stream().filter(p -> !bare.matcher(p).matches()).toList();
                    if (keep.size() != parts.size()) {
                        cells.set(q, keep.isEmpty() ? Prompt1Tsv.EMPTY : String.join(" ;; ", keep));
                        dropped.add(cells.get(0));
                    }
                }
                lines.set(n, String.join("\t", cells));
            }
            blocks.put("meanings.tsv", String.join("\n", lines));
            if (!joined.isEmpty()) {
                notes.add("kwalificatie 'categorie<tab>tekst' samengevoegd tot 'categorie=tekst': " + String.join(", ", joined));
            }
            if (!dropped.isEmpty()) {
                notes.add("kwalificatie zonder tekst weggelaten: " + String.join(", ", dropped));
            }
        }
        if (observationTexts != null && !observationTexts.isEmpty()) {
            List<String> cased = new ArrayList<>();
            for (String name : List.of("meanings.tsv", "links.tsv")) {
                if (!blocks.containsKey(name)) {
                    continue;
                }
                List<String> rows = new ArrayList<>(stripNewlines(blocks.get(name)).lines().toList());
                List<String> columns = List.of(rows.get(0).split("\t", -1));
                int e = columns.indexOf("source_excerpt");
                if (e < 0) {
                    continue;
                }
                for (int n = 1; n < rows.size(); n++) {
                    String[] cells = rows.get(n).split("\t", -1);
                    if (cells.length != columns.size()) {
                        continue;
                    }
                    Map<String, String> row = new HashMap<>();
                    for (int i = 0; i < cells.length; i++) {
                        row.put(columns.get(i), cells[i]);
                    }
                    List<String> carriers;
                    if (name.equals("meanings.tsv")) {
                        carriers = Prompt1Tsv.ids(row.getOrDefault("carrier_observation_ids", ""));
                        if (carriers.isEmpty()) {
                            carriers = List.of(row.getOrDefault("observation_id", ""));
                        }
                    } else {
                        carriers = List.of(row.getOrDefault("observation_id", ""));
                    }
                    String fixed = fixCase(cells[e], carriers.stream().map(o -> observationTexts.getOrDefault(o, "")).toList());
                    if (!fixed.equals(cells[e])) {
                        cells[e] = fixed;
                        rows.set(n, String.join("\t", cells));
                        cased.add(cells[0]);
                    }
                }
                blocks.put(name, String.join("\n", rows));
            }
            if (!cased.isEmpty()) {
                notes.add("excerpt gelijkgemaakt aan de hoofdletters van de bron: " + String.join(", ", cased));
            }
        }
        if (blocks.containsKey("links.tsv")) {
            List<String> rows = stripNewlines(blocks.get("links.tsv")).lines().toList();
            StringBuilder rest = new StringBuilder();
            blocks.forEach((name, body) -> {
                if (!name.equals("links.tsv")) {
                    rest.append(body).append('\n');
                }
            });
            List<String> columns = List.of(rows.get(0).split("\t", -1));
            List<String> keep = new ArrayList<>(List.of(rows.get(0)));
            List<String> gone = new ArrayList<>();
            for (String line : rows.subList(1, rows.size())) {
                String[] cells = line.split("\t", -1);
                Map<String, String> row = new HashMap<>();
                for (int i = 0; i < columns.size() && i < cells.length; i++) {
                    row.put(columns.get(i), cells[i]);
                }
                String from = row.get("from_meaning_id");
                String linkId = row.getOrDefault("link_id", "");
                if (from != null && !from.isEmpty() && from.equals(row.get("to_meaning_id"))
                        && !Pattern.compile("\\b" + Pattern.quote(linkId) + "\\b").matcher(rest).find()) {
                    gone.add(linkId);
                } else {
                    keep.add(line);
                }
            }
            blocks.put("links.tsv", String.join("\n", keep));
            if (!gone.isEmpty()) {
                notes.add("link van een betekenis naar zichzelf weggelaten: " + String.join(", ", gone));
            }
        }
        return notes;
    }

    /**
     * Qualifications written as 'category&lt;TAB&gt;wording' instead of 'category=wording' spread over
     * extra columns (one tab per pair, also inside a ';;' list). Each column break is joined back as
     * '=' only where the text before it ends in a bare category, and the result is accepted only when
     * every part reads 'category=wording' with one of the 11 categories (Jan, 10 Nov 2026).
     * Returns null when the row does not have that form; it is then left as it is, so the check refuses it.
     */
    static String joinQualificationColumns(List<String> columns, Pattern bare) {
        StringBuilder merged = new StringBuilder(columns.get(0).stripTrailing());
        for (String next : columns.subList(1, columns.size())) {
            String[] parts = merged.toString().split(";;", -1);
            String last = parts[parts.length - 1].strip();
            if (!bare.matcher(last).matches()) {
                return null;
            }
            merged.append('=').append(next.strip());
        }
        for (String part : merged.toString().split(";;", -1)) {
            String p = part.strip();
            int eq = p.indexOf('=');
            if (eq <= 0 || !bare.matcher(p.substring(0, eq).strip()).matches()) {
                return null;
            }
        }
        return merged.toString();
    }

    /** member_ids of a direction are exactly its grouping rows with decision = member (functional contributions). */
    static String syncMembers(String meanings, String grouping, String directions, List<String> fixed) {
        Set<String> contributions = admitted(meanings);
        Map<String, List<String>> want = new LinkedHashMap<>();
        for (Map<String, String> g : Prompt1Tsv.rows(grouping)) {
            if ("member".equals(g.getOrDefault("decision", "").strip()) && contributions.contains(g.get("meaning_id"))) {
                want.computeIfAbsent(g.get("direction_id"), d -> new ArrayList<>()).add(g.get("meaning_id"));
            }
        }
        List<String> lines = new ArrayList<>(stripNewlines(directions).lines().toList());
        List<String> head = List.of(lines.get(0).split("\t", -1));
        int i = head.indexOf("member_ids");
        if (i < 0) {
            return directions;
        }
        for (int n = 1; n < lines.size(); n++) {
            String[] cells = lines.get(n).split("\t", -1);
            if (cells.length != head.size() || !want.containsKey(cells[0])) {
                continue;
            }
            List<String> old = Prompt1Tsv.commaList(cells[i]);
            List<String> wanted = want.get(cells[0]);
            List<String> updated = new ArrayList<>(old.stream().filter(x -> wanted.contains(x)).toList());
            wanted.stream().filter(x -> !old.contains(x)).forEach(x -> updated.add(x));
            if (!updated.equals(old)) {
                cells[i] = String.join(",", updated);
                lines.set(n, String.join("\t", cells));
                fixed.add(cells[0]);
            }
        }
        return String.join("\n", lines);
    }

    /** context_ids hold only retained_context meanings: a functional contribution there is dropped. */
    static String dropContributionContext(String meanings, String directions, List<String> notes) {
        Set<String> contributions = admitted(meanings);
        List<String> lines = new ArrayList<>(stripNewlines(directions).lines().toList());
        List<String> head = List.of(lines.get(0).split("\t", -1));
        int i = head.indexOf("context_ids");
        if (i < 0) {
            return directions;
        }
        for (int n = 1; n < lines.size(); n++) {
            String[] cells = lines.get(n).split("\t", -1);
            if (cells.length != head.size()) {
                continue;
            }
            List<String> ids = Prompt1Tsv.commaList(cells[i]);
            List<String> keep = ids.stream().filter(x -> !contributions.contains(x)).toList();
            if (!keep.equals(ids)) {
                cells[i] = keep.isEmpty() ? Prompt1Tsv.EMPTY : String.join(",", keep);
                lines.set(n, String.join("\t", cells));
                notes.add(cells[0] + ": " + String.join(", ", ids.stream().filter(x -> contributions.contains(x)).toList()));
            }
        }
        return String.join("\n", lines);
    }

    /** For a non-dominant direction, basis_meaning_ids keep only its own members (when at least one remains). */
    static String trimPriorityBasis(String directions, String priority, List<String> notes) {
        Map<String, Set<String>> own = new HashMap<>();
        for (Map<String, String> d : Prompt1Tsv.rows(directions)) {
            Set<String> members = new HashSet<>();
            for (String x : d.getOrDefault("member_ids", "").split(",")) {
                members.add(x.strip());
            }
            own.put(d.get("direction_id"), members);
        }
        List<String> lines = new ArrayList<>(stripNewlines(priority).lines().toList());
        List<String> head = List.of(lines.get(0).split("\t", -1));
        if (!head.containsAll(List.of("direction_id", "priority_status", "basis_meaning_ids"))) {
            return priority;
        }
        int i = head.indexOf("basis_meaning_ids");
        int status = head.indexOf("priority_status");
        for (int n = 1; n < lines.size(); n++) {
            String[] cells = lines.get(n).split("\t", -1);
            if (cells.length != head.size() || !"non_dominant".equals(cells[status].strip()) || !own.containsKey(cells[0])) {
                continue;
            }
            List<String> ids = Prompt1Tsv.commaList(cells[i]);
            List<String> keep = ids.stream().filter(x -> own.get(cells[0]).contains(x)).toList();
            if (!keep.isEmpty() && !keep.equals(ids)) {
                cells[i] = String.join(",", keep);
                lines.set(n, String.join("\t", cells));
                notes.add(cells[0] + ": " + String.join(", ", ids.stream().filter(x -> !keep.contains(x)).toList()));
            }
        }
        return String.join("\n", lines);
    }

    /** The meaning of an empty intake field is fixed: the backend writes it (retained_context, the exact value). */
    static String addNullMeanings(String observations, String meanings, List<String> added) {
        List<Map<String, String>> nulls = Prompt1Tsv.rows(observations).stream()
                .filter(o -> Prompt1TurnInventory.isNullField(o)).toList();
        List<String> lines = new ArrayList<>(stripNewlines(meanings).lines().toList());
        List<String> head = List.of(lines.get(0).split("\t", -1));
        Set<String> have = new HashSet<>();
        lines.subList(1, lines.size()).forEach(line -> have.add(line.split("\t", -1)[0]));
        for (Map<String, String> o : nulls) {
            String observationId = o.get("observation_id");
            boolean written = lines.subList(1, lines.size()).stream().anyMatch(line -> {
                String[] cells = line.split("\t", -1);
                return cells.length > 1 && cells[1].equals(observationId);
            });
            if (written) {
                continue;
            }
            String id = String.format("M%03dn", Prompt1Tsv.digits(observationId));
            while (have.contains(id)) {
                id += "n";
            }
            Map<String, String> row = new HashMap<>();
            row.put("meaning_id", id);
            row.put("observation_id", observationId);
            row.put("carrier_observation_ids", observationId);
            row.put("meaning_type", "state_or_measurement");
            row.put("admission", "retained_context");
            row.put("source_excerpt", o.get("source_value"));
            row.put("recognized_meaning", "Geen waarde opgegeven voor " + o.get("source_label") + ".");
            row.put("qualifications", Prompt1Tsv.EMPTY);
            row.put("admission_basis", "Leeg veld; geen gegeven.");
            String line = String.join("\t", head.stream().map(h -> row.getOrDefault(h, Prompt1Tsv.EMPTY)).toList());
            int at = lines.size();
            for (int n = 1; n < lines.size(); n++) {
                String[] cells = lines.get(n).split("\t", -1);
                if (Prompt1Tsv.digits(cells.length > 1 ? cells[1] : "") > Prompt1Tsv.digits(observationId)) {
                    at = n;
                    break;
                }
            }
            lines.add(at, line);
            have.add(id);
            added.add(id);
        }
        return String.join("\n", lines);
    }

    /** observation_ids, source_excerpt, recognized_meaning and qualifications of the evidence basis copy meanings.tsv. */
    static String fillEvidenceCopies(String meanings, String basis) {
        Map<String, Map<String, String>> byId = new HashMap<>();
        Prompt1Tsv.rows(meanings).forEach(row -> byId.put(row.get("meaning_id"), row));
        List<String> lines = new ArrayList<>(stripNewlines(basis).lines().toList());
        List<String> head = List.of(lines.get(0).split("\t", -1));
        String[][] columns = {{"observation_ids", "carrier_observation_ids"}, {"source_excerpt", "source_excerpt"},
                {"recognized_meaning", "recognized_meaning"}, {"qualifications", "qualifications"}};
        for (int n = 1; n < lines.size(); n++) {
            String[] cells = lines.get(n).split("\t", -1);
            if (cells.length != head.size() || !byId.containsKey(cells[0])) {
                continue;
            }
            for (String[] column : columns) {
                int i = head.indexOf(column[0]);
                if (i >= 0) {
                    String value = byId.get(cells[0]).get(column[1]);
                    cells[i] = value == null || value.isEmpty() ? Prompt1Tsv.EMPTY : value;
                }
            }
            lines.set(n, String.join("\t", cells));
        }
        return String.join("\n", lines);
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    /** The meanings admitted as functional contributions. */
    private static Set<String> admitted(String meanings) {
        Set<String> out = new HashSet<>();
        for (Map<String, String> row : Prompt1Tsv.rows(meanings)) {
            if ("functional_contribution".equals(row.get("admission"))) {
                out.add(row.get("meaning_id"));
            }
        }
        return out;
    }

    /** Python's {@code strip("\n")}. */
    static String stripNewlines(String text) {
        return Prompt1Tsv.strip(text, "\n");
    }

    private static String file(Map<String, String> files, String name) {
        String content = files.get(name);
        if (content == null) {
            throw new IllegalStateException("Working file " + name + " is missing for this turn");
        }
        return content;
    }

    /** A repair that fails on a malformed file is skipped; the checks after it report the file. */
    private static void quietly(Runnable repair) {
        try {
            repair.run();
        } catch (RuntimeException e) {
            // the checks below report a malformed file
        }
    }

    static String json(JsonNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot write the final JSON", e);
        }
    }
}
