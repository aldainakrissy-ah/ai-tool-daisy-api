package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1SourcePackageService.KeyText;

import java.util.*;
import java.util.function.Predicate;

/**
 * The user message of each turn, ported from Jan's {@code daisy_preload.py} ({@code prompt},
 * {@code make}, {@code files_block}, {@code filtered_block}, {@code keys_block}). The texts come
 * from the turn configuration; this class only assembles them with the run's working files and
 * the source keys, in Jan's order and layout.
 */
public final class Prompt1TurnMessages {

    /** Step 1 and Step 2 keys the A1 turn receives (SM 0.3). */
    public static final List<String> A1_STEP1_KEYS = List.of("01:1.2", "01:1.3", "01:2.1", "01:2.2");
    public static final List<String> A1_STEP2_KEYS = List.of("02:B.55.1", "02:B.55.2", "03:1.138", "03:1.338");

    private Prompt1TurnMessages() {
    }

    /** The message frame every turn but C shares: title, common text, steps, files, run data, then the parts. */
    public static String prompt(Prompt1TurnConfig config, String turn, String runLabel, List<String> parts) {
        Prompt1TurnConfig.Turn table = config.turns().get(turn);
        if (table == null) {
            throw new IllegalArgumentException("Unknown turn " + turn);
        }
        List<String> lines = new ArrayList<>(List.of(
                config.promptHeader().replace("{turn}", turn), "", config.commonText(),
                "STEPS OF THIS TURN: " + table.steps(), "FILES TO RETURN: " + String.join(", ", table.files()), "",
                "RUN DATA", "run_label: " + runLabel));
        lines.addAll(parts);
        return String.join("\n", lines) + "\n";
    }

    /**
     * Turn A1 (Steps 1–3): the closed Step 0 inventory without the empty intake fields, the Step 1
     * and Step 2 keys and the doc 18 parts for Steps 1–3.
     */
    public static String a1(Prompt1TurnConfig config, String runLabel, String runDate, String manifest,
                            String observations, List<KeyText> step1Keys, List<KeyText> step2Keys, List<String> doc18Part) {
        List<String> nulls = Prompt1Tsv.rows(observations).stream()
                .filter(row -> Prompt1TurnInventory.isNullField(row))
                .map(row -> row.get("observation_id")).toList();
        String intro = config.text("a1");
        if (!nulls.isEmpty()) {
            intro += config.text("a1Nulls", Map.of("nulls", String.join(", ", nulls)));
        }
        List<String> parts = new ArrayList<>(List.of(intro, "CURRENT_RUN_DATE: " + runDate, "",
                "=== CLOSED STEP 0 INVENTORY (server) ==="));
        parts.add(filesBlock("manifest.tsv", manifest));
        parts.add(filteredBlock("observations.tsv", observations, row -> !Prompt1TurnInventory.isNullField(row)));
        parts.add("");
        parts.add("=== STEP 1 KEYS ===");
        parts.addAll(keysBlock(config, step1Keys));
        parts.add("");
        parts.add("=== STEP 2 KEYS ===");
        parts.addAll(keysBlock(config, step2Keys));
        parts.addAll(doc18Part);
        return prompt(config, "A1", runLabel, parts);
    }

    /** Reads source keys for a step (the runner logs each read in retrieval_log.tsv). */
    public interface Sources {
        List<KeyText> keys(String step, List<String> keys);

        /** The R4 lookup table of Step 4. */
        KeyText r4Lookup(String step);
    }

    /** Turn A2 (Step 4): the closed Steps 1–3 in play, the R4 lookup, and per IE its R4:01 and R5:ELEM keys. */
    public static String a2(Prompt1TurnConfig config, String runLabel, Map<String, String> files, Sources sources,
                            List<String> doc18Part) {
        List<String> parts = new ArrayList<>(List.of(config.text("a2") + config.text("trimNote"), "",
                "=== CLOSED STEPS 1–3 ==="));
        parts.addAll(closedSteps1To3(files));
        parts.addAll(List.of("", "=== STEP 4: R4 LOOKUP ===", text(sources.r4Lookup("4"))));
        // every IE's two keys in one read (one retrieval-log write), in the same order as one read per IE
        List<String> keys = new ArrayList<>();
        config.ies().forEach(ie -> keys.addAll(List.of("R4:" + ie + ":01", "R5:ELEM:" + ie)));
        List<KeyText> texts = sources.keys("4", keys);
        for (int i = 0; i < config.ies().size(); i++) {
            parts.add("");
            parts.add("=== STEP 4: IE " + config.ies().get(i) + " ===");
            parts.addAll(keysBlock(config, texts.subList(2 * i, 2 * i + 2)));
        }
        parts.addAll(doc18Part);
        return prompt(config, "A2", runLabel, parts);
    }

    /** Turn A3 (Step 5): Steps 1–4 with the derived candidates, the partial-fit note if needed, the candidates' R4 entries. */
    public static String a3(Prompt1TurnConfig config, String runLabel, Map<String, String> files, Sources sources,
                            List<String> doc18Part) {
        StringBuilder intro = new StringBuilder(config.text("a3")).append(config.text("trimNote"));
        List<String> body = new ArrayList<>(closedSteps1To3(files));
        // v06a: only the passes = yes rows of the core filter, with a note that every other pair is no
        body.add(filteredBlock("ie_core_filter.tsv", file(files, "ie_core_filter.tsv"),
                row -> "yes".equals(row.getOrDefault("passes", "").strip())));
        intro.append(config.text("a3YesOnly"));
        body.add(filesBlock("ie_membership.tsv", file(files, "ie_membership.tsv")));
        body.add(filesBlock("a1_ie_candidates.tsv", file(files, "a1_ie_candidates.tsv")));
        List<String> candidates = new ArrayList<>(Prompt1Tsv.rows(file(files, "a1_ie_candidates.tsv")).stream()
                .map(row -> row.get("ie_ref")).toList());
        List<String> others = List.copyOf(candidates);
        Map<String, List<String>> partialFit = Prompt1TurnRules.partialFit(file(files, "a1_ie_candidates.tsv"),
                file(files, "priority.tsv"), file(files, "ie_core_filter.tsv"));
        if (!partialFit.isEmpty()) {
            List<String> listed = new ArrayList<>();
            partialFit.forEach((ie, ftfs) -> listed.add(ie + ": " + String.join(", ", ftfs)));
            intro.append(config.text("a3PartialFit", Map.of("candidates", String.join("; ", listed))));
            body.add(filesBlock("ie_ftf_cells.tsv", file(files, "ie_ftf_cells.tsv")));
            partialFit.keySet().stream().filter(ie -> !candidates.contains(ie)).forEach(ie -> candidates.add(ie));
        }
        intro.append(config.text("a3Others", Map.of("others", others.isEmpty() ? Prompt1Tsv.EMPTY : String.join(", ", others))));
        List<String> parts = new ArrayList<>(List.of(intro.toString(), "",
                "=== CLOSED STEPS 1–4 (with the server results of derive_ie_candidates) ==="));
        parts.addAll(body);
        parts.addAll(List.of("", "=== R4 ENTRIES OF THE CANDIDATE IEs ==="));
        parts.addAll(keysBlock(config, sources.keys("5", candidates.stream().map(ie -> "R4:" + ie + ":01").toList())));
        parts.addAll(doc18Part);
        return prompt(config, "A3", runLabel, parts);
    }

    /**
     * Turn C for one candidate HETA (Steps 9–10): the closed evidence basis with the original
     * observations, the HETA's Step 9 keys of R3 v06 and its fixed signal list.
     */
    public static String c(Prompt1TurnConfig config, String runLabel, String heta, Map<String, String> files,
                           Sources sources) {
        List<Map<String, String>> candidates = Prompt1Tsv.rows(file(files, "a3_candidates.tsv"));
        if (candidates.stream().noneMatch(row -> heta.equals(row.get("heta_ref")))) {
            throw new IllegalStateException(heta + " is not in a3_candidates.tsv");
        }
        String primary = candidates.get(0).get("primary_ie_ref");
        Map<String, Map<String, String>> observations = Prompt1Tsv.byId(file(files, "observations.tsv"), "observation_id");
        Map<String, Map<String, String>> links = Prompt1Tsv.byId(file(files, "links.tsv"), "link_id");
        Map<String, Map<String, String>> meanings = Prompt1Tsv.byId(file(files, "meanings.tsv"), "meaning_id");
        List<String> keys = new ArrayList<>(List.of("R3:" + heta + ".2", "R3:" + heta + ".4", "R3:" + heta + ".4:" + primary));
        if (!config.sigAbsent().contains(heta)) {
            keys.add("R3:" + heta + ".4:SIG");
        }
        keys.add("R3:" + heta + ".5.2");
        List<KeyText> keyTexts = sources.keys("9", keys);

        List<String> lines = new ArrayList<>(List.of(config.cInstructionText(), "run_label: " + runLabel, "HETA_REF: " + heta,
                "A1.primary: " + primary, "",
                "A3_EVIDENCE_BASIS (Step 8, closed; role, direction, FTF, link, meaning, original observation)"));
        List<String> seen = new ArrayList<>();
        for (Map<String, String> basis : Prompt1Tsv.rows(file(files, "a3_evidence_basis.tsv"))) {
            Map<String, String> meaning = meanings.getOrDefault(basis.get("meaning_id"), Map.of());
            lines.add("- " + String.join(" | ", basis.get("meaning_id"), basis.get("role"), basis.get("direction_id"),
                    or(basis.get("ftf_id")), or(basis.get("link_id")), or(basis.get("relation"))));
            for (String column : List.of("recognized_meaning", "source_excerpt", "qualifications")) {
                String own = basis.get(column);
                String value = own != null && !own.isEmpty() ? own : meaning.getOrDefault(column, Prompt1Tsv.EMPTY);
                if (!column.equals("qualifications") || !(value.isEmpty() || Prompt1Tsv.EMPTY.equals(value))) {
                    lines.add("  " + column + ": " + value);
                }
            }
            Map<String, String> link = links.get(basis.getOrDefault("link_id", ""));
            if (link != null) {
                lines.add("  link " + link.get("link_id") + ": " + link.get("from_meaning_id") + " → " + link.get("to_meaning_id")
                        + " (" + link.get("relation_type") + "); excerpt: " + link.get("source_excerpt") + "; scope: " + link.get("scope"));
            }
            String observationIds = basis.get("observation_ids");
            if (observationIds == null || observationIds.isEmpty()) {
                observationIds = meaning.getOrDefault("observation_id", "") + "," + meaning.getOrDefault("carrier_observation_ids", "");
            }
            for (String id : observationIds.split(",")) {
                String observationId = id.strip();
                if (observations.containsKey(observationId) && !seen.contains(observationId)) {
                    seen.add(observationId);
                }
            }
        }
        lines.addAll(List.of("", "ORIGINAL OBSERVATIONS (for verification of the copies)"));
        for (String id : seen) {
            Map<String, String> o = observations.get(id);
            lines.add("- " + id + " | " + o.get("source_id") + " | " + o.get("source_label") + " | " + o.get("source_value"));
        }
        lines.addAll(List.of("", "STEP 9 KEYS (R3 v06, retrieved by the backend)"));
        for (String text : keysBlock(config, keyTexts)) {
            lines.add("\n" + text);
        }
        KeyText signalSection = keyTexts.get(keyTexts.size() - 1);
        List<String> signals = signalSection.found() ? Prompt1TurnRules.r3Signals(signalSection.content()) : List.of();
        if (!signals.isEmpty()) {
            lines.addAll(List.of("", "SIGNALS (SM 12.1, fixed by the backend: one per listed line of R3:" + heta
                    + ".5.2, in source order)"));
            for (int i = 0; i < signals.size(); i++) {
                lines.add("S" + (i + 1) + " | " + signals.get(i));
            }
        }
        return String.join("\n", lines) + "\n";
    }

    private static final java.util.regex.Pattern REPAIR_ID = java.util.regex.Pattern.compile(
            "(?<![\\w.])(?:M\\d{3}[a-z]?n?|FD_\\d{3}|FTF_\\d{3}|L\\d{3}|OBS_\\d{3}|22\\.\\d{1,2}(?:-A\\d+-[EQC]\\d+)?)(?![\\w.])");
    private static final java.util.regex.Pattern MEANING_ID = java.util.regex.Pattern.compile("M\\d{3}[a-z]?n?");
    private static final java.util.regex.Pattern DIRECTION_ID = java.util.regex.Pattern.compile("FD_\\d{3}");
    private static final java.util.regex.Pattern IE_REF = java.util.regex.Pattern.compile("22\\.\\d{1,2}");

    /**
     * v05: the small repair turn for a refused A1 or A2 answer, instead of a full correction that resends
     * the whole turn. It holds the problems, the rows of the answer and of the original prompt that concern
     * the IDs named in the problems (expanded: FTF ↔ FD, a direction's members and context, a meaning's
     * observations; for A2 the source keys of the named IEs), and for A1 an overview of all directions.
     * The model returns only the rows to add or change ({@link Prompt1TurnAnswers#mergePatch}). Jan's
     * {@code repair_prompt}.
     */
    public static String repair(Prompt1TurnConfig config, String turn, String runLabel, String problems, String answer,
                                String promptText) {
        Map<String, String> blocks = Prompt1TurnAnswers.blocks(answer);
        Set<String> ids = new HashSet<>();
        java.util.regex.Matcher found = REPAIR_ID.matcher(problems);
        while (found.find()) {
            ids.add(found.group());
        }
        List<String> sources = new ArrayList<>(blocks.values());
        sources.add(promptText);
        for (int round = 0; round < 2; round++) {
            Set<String> more = new HashSet<>();
            ids.stream().filter(x -> x.startsWith("FTF_")).forEach(x -> more.add(Prompt1Tsv.directionOf(x)));
            ids.stream().filter(x -> x.startsWith("FD_")).forEach(x -> more.add(Prompt1Tsv.ftfOf(x)));
            ids.stream().filter(x -> x.contains("-A")).forEach(x -> more.add(x.split("-")[0]));
            ids.addAll(more);
            for (String text : sources) {
                for (String line : text.split("\n", -1)) {
                    String[] c = line.split("\t", -1);
                    if (c.length > 4 && ids.contains(c[0]) && DIRECTION_ID.matcher(c[0]).matches()) {
                        tokens(String.join("\t", Arrays.copyOfRange(c, 1, c.length))).stream()
                                .filter(x -> MEANING_ID.matcher(x).matches()).forEach(x -> ids.add(x));
                    }
                    if (c.length > 2 && DIRECTION_ID.matcher(c[0]).matches() && ids.contains(c[1])) {
                        ids.add(c[0]);  // grouping row of a named meaning
                    }
                    if (c.length > 2 && ids.contains(c[0]) && MEANING_ID.matcher(c[0]).matches()) {
                        tokens(c[1] + "\t" + c[2]).stream().filter(x -> x.startsWith("OBS_")).forEach(x -> ids.add(x));
                    }
                }
            }
        }
        Set<String> namedIes = new HashSet<>();
        ids.stream().filter(x -> IE_REF.matcher(x).matches()).forEach(x -> namedIes.add(x));
        List<String> turnFiles = config.turns().get(turn).files();
        List<String> keys = new ArrayList<>();
        Prompt1TurnAnswers.REPAIR_KEYS.forEach((name, key) -> {
            if (turnFiles.contains(name)) {
                keys.add(name + ": " + String.join(", ", key));
            }
        });
        List<String> out = new ArrayList<>(List.of("DAISY PROMPT 1 — REPAIR OF TURN " + turn + " (PRELOAD)", "",
                "The backend checked your answer for turn " + turn + ". Everything is accepted except these problems:",
                problems.strip(), "",
                "Fix only these problems, exactly by the SM, and change nothing else. Below are the rows of your answer and of the "
                        + "closed input that concern them. ANSWER FORMAT — return, for each file you change, one fenced block with the file "
                        + "name after the opening fence, the header line, and only the rows you add or change: a row replaces the row with "
                        + "the same key (" + String.join("; ", keys) + "), a new key adds a row, and a row whose first cell after the key "
                        + "is DELETE removes that row. Rows you do not return stay as they are. No other text.",
                "", "run_label: " + runLabel));
        out.addAll(List.of("", "=== ROWS OF YOUR ANSWER THAT CONCERN THESE IDs ==="));
        for (String name : turnFiles) {
            if (!blocks.containsKey(name)) {
                continue;
            }
            List<String> lines = Prompt1TurnAnswers.stripNewlines(blocks.get(name)).lines().toList();
            List<String> rows = lines.subList(1, lines.size()).stream()
                    .filter(l -> tokens(l).stream().anyMatch(t -> ids.contains(t))).toList();
            if (!rows.isEmpty()) {
                out.add("--- " + name + " ---");
                out.add(lines.get(0));
                out.addAll(rows);
            }
        }
        if (turn.equals("A1") && blocks.containsKey("directions.tsv")) {
            Map<String, String> cts = new HashMap<>();
            Prompt1Tsv.rows(blocks.get("cts_admission.tsv")).forEach(r -> cts.put(r.get("direction_id"), r.get("decision")));
            Map<String, String> priority = new HashMap<>();
            Prompt1Tsv.rows(blocks.get("priority.tsv")).forEach(r -> priority.put(r.get("direction_id"), r.get("priority_status")));
            out.addAll(List.of("", "=== ALL DIRECTIONS OF YOUR ANSWER (direction_id | identity | CTS | priority) ==="));
            for (Map<String, String> d : Prompt1Tsv.rows(blocks.get("directions.tsv"))) {
                String id = d.get("direction_id");
                out.add(id + " | " + d.get("identity") + " | " + cts.getOrDefault(id, Prompt1Tsv.EMPTY) + " | "
                        + priority.getOrDefault(id, Prompt1Tsv.EMPTY));
            }
        }
        out.addAll(List.of("", "=== CLOSED INPUT THAT CONCERNS THESE IDs ==="));
        String section = "";
        boolean keepSection = false;
        for (String line : promptText.split("\n", -1)) {
            if (line.startsWith("=== ")) {
                section = line;
                keepSection = line.startsWith("=== WORKING-FILE FORMAT") || (turn.equals("A2") && namedIes.stream()
                        .anyMatch(ie -> java.util.regex.Pattern.compile("\\bIE " + java.util.regex.Pattern.quote(ie) + " ===")
                                .matcher(line).find()));
                if (keepSection) {
                    out.add(line);
                }
                continue;
            }
            if (keepSection) {
                out.add(line);
            } else if (ids.contains(line.split("\t", -1)[0]) || ((line.startsWith("OBS_") || line.startsWith("M")
                    || line.startsWith("FD_") || line.startsWith("L")) && line.contains("\t")
                    && tokens(line).stream().anyMatch(t -> ids.contains(t)))) {
                out.add("[" + Prompt1Tsv.strip(section, "= ") + "] " + line);
            }
        }
        return String.join("\n", out) + "\n";
    }

    /** Python's {@code set(re.split(r"[\t, ;]+", line))}. */
    private static Set<String> tokens(String line) {
        return new HashSet<>(Arrays.asList(line.split("[\t, ;]+", -1)));
    }

    /**
     * Steps 1–3 for turns A2 and A3: only the directions in play, the meanings they use and the
     * directions of meanings linked to them (Jan's {@code closed_1_3}).
     */
    static List<String> closedSteps1To3(Map<String, String> files) {
        Prompt1TurnRules.InPlay inPlay = Prompt1TurnRules.inPlay(file(files, "cts_admission.tsv"),
                file(files, "directions.tsv"), file(files, "links.tsv"));
        return List.of(
                filteredBlock("meanings.tsv", file(files, "meanings.tsv"), row -> inPlay.meanings().contains(row.get("meaning_id"))),
                filesBlock("links.tsv", file(files, "links.tsv")),
                filteredBlock("directions.tsv", file(files, "directions.tsv"),
                        row -> inPlay.directions().contains(row.get("direction_id"))),
                filteredBlock("cts_admission.tsv", file(files, "cts_admission.tsv"),
                        row -> inPlay.directions().contains(row.get("direction_id"))),
                filesBlock("priority.tsv", file(files, "priority.tsv")));
    }

    private static String file(Map<String, String> files, String name) {
        String content = files.get(name);
        if (content == null) {
            throw new IllegalStateException("Working file " + name + " is missing for this turn");
        }
        return content;
    }

    private static String or(String value) {
        return value == null || value.isEmpty() ? Prompt1Tsv.EMPTY : value;
    }

    private static String text(KeyText key) {
        return key.found() ? key.text() : "[" + key.key() + "] NOT FOUND";
    }

    /** A whole working file under its name. */
    public static String filesBlock(String name, String content) {
        return "=== " + name + " ===\n" + content.strip();
    }

    /** A working file with only the rows the turn needs; the header line is kept. */
    public static String filteredBlock(String name, String content, Predicate<Map<String, String>> keep) {
        List<String> lines = content.replace("\r\n", "\n").strip().lines().toList();
        String[] head = lines.get(0).split("\t", -1);
        List<String> out = new ArrayList<>(List.of(lines.get(0)));
        for (String line : lines.subList(1, lines.size())) {
            if (line.isBlank()) {
                continue;
            }
            String[] cells = line.split("\t", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < head.length && i < cells.length; i++) {
                row.put(head[i], cells[i]);
            }
            if (keep.test(row)) {
                out.add(line);
            }
        }
        return "=== " + name + " (rows for this turn) ===\n" + String.join("\n", out);
    }

    /** The key texts in order; a key that does not exist is shown as NOT FOUND (a missing signature is no gap). */
    public static List<String> keysBlock(Prompt1TurnConfig config, List<KeyText> keys) {
        List<String> out = new ArrayList<>();
        for (KeyText key : keys) {
            if (key.found()) {
                out.add(key.text().strip());
            } else {
                out.add("[" + key.key() + "] NOT FOUND" + (key.key().endsWith(":SIG") ? config.text("sigNotFound") : ""));
            }
        }
        return out;
    }
}
