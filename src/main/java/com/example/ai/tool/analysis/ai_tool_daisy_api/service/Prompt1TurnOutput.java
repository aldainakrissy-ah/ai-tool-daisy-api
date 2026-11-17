package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The fixed-rule steps after the C turns, which Jan's backend executes since v04z/v06 (SM v0.136):
 * the 12.4 projection of a C decision, the B2 needs and coverage from the fixed matrix (15.1, 15.2),
 * the GAP numbering, B1 (Step 12), the YSQ trigger (15.3), B3 (Step 14) and the final JSON
 * assembled from the closed working files. Ported from {@code daisy_preload.py}
 * ({@code project_decision}, {@code b2_matrix}, {@code b2_from_matrix}, {@code number_gaps},
 * {@code b1_items}, {@code ysq_trigger}, {@code q_labels}, {@code age_on}, {@code assemble_output}).
 * A rule that cannot be applied throws {@link IllegalStateException}: the run stops, as in Jan's backend.
 */
public final class Prompt1TurnOutput {

    /** The worksheets of schema v0.46d-lean, in Jan's order. */
    static final List<String> WORKSHEETS = List.of("tasks", "retrieval_log", "observations", "meanings", "links",
            "grouping", "directions", "cts_admission", "priority", "ie_core_filter", "ie_ftf_cells", "ie_membership",
            "a1_ie_candidates", "a1_selection", "heta_canon", "heta_cells", "b2_needs", "b2_coverage");

    private static final Pattern NUMBER_CELL = Pattern.compile("\\**\\d+\\**");
    private static final Pattern LINEAR_ROW = Pattern.compile(
            "\\**(\\d+)\\**, \\**(?:Aanvullende vragenlijst|Vragenlijst)\\** = (.+?)\\. \\**\\1\\**, \\**QEST-JSON\\** = ([\\w-]+)");
    private static final Pattern SECTION_2 = Pattern.compile("2\\.[1-8]\\.\\d+");
    private static final Pattern HETA_ID = Pattern.compile("\\bHETA_ID:\\s*(.+?)\\s*(?:HETA_RECORD_END|$)", Pattern.MULTILINE);
    private static final Pattern HETA_JSON_IDENTIFIER = Pattern.compile("\\bHETA_JSON_IDENTIFIER:\\s*([\\w-]+)");
    private static final Pattern HETA_APPENDIX_REF = Pattern.compile("\\bHETA_APPENDIX_REF:\\s*([\\d.]+)");
    private static final Map<String, Integer> MONTHS = new HashMap<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        String[][] names = {{"jan", "january", "januari"}, {"feb", "february", "februari"}, {"mar", "mrt", "march", "maart"},
                {"apr", "april"}, {"may", "mei"}, {"jun", "june", "juni"}, {"jul", "july", "juli"}, {"aug", "august", "augustus"},
                {"sep", "sept", "september"}, {"oct", "okt", "october", "oktober"}, {"nov", "november"}, {"dec", "december"}};
        for (int month = 0; month < names.length; month++) {
            for (String name : names[month]) {
                MONTHS.put(name, month + 1);
            }
        }
    }

    private Prompt1TurnOutput() {
    }
    // ---- Step 10: the 12.4 projection of a C decision -----------------------------------------------------

    /** A projected decision row and the values that differed from the model's answer. */
    public record Decision(Map<String, String> row, List<String> notes) {
    }

    /**
     * SM 12.4 as a projection, no reassessment: heta_name, classification, decision_route and
     * used_indications follow from the HETA's cells. direct = an own contribution with S; indirect = a
     * contribution with PART or an applicable signal with S; otherwise not_eligible. The model keeps
     * decisive_meaning_ids and basis.
     *
     * @param hetaName the name from KEY R3:&lt;heta&gt;.2, or "" when it cannot be read
     */
    static Decision projectDecision(String heta, String hetaName, List<Map<String, String>> canon,
                                    List<Map<String, String>> cells, Map<String, String> decision) {
        Set<String> applicable = new HashSet<>();
        canon.stream().filter(c -> "yes".equals(c.get("applicable"))).forEach(c -> applicable.add(c.get("canonical_id")));
        List<String> fc = cells.stream().filter(x -> "functional_correspondence".equals(x.get("route")))
                .map(x -> x.get("state")).toList();
        List<String> sg = cells.stream().filter(x -> "canonical_signal".equals(x.get("route"))
                && applicable.contains(x.get("canonical_id"))).map(x -> x.get("state")).toList();
        String classification = fc.contains("S") ? "direct" : (fc.contains("PART") || sg.contains("S")) ? "indirect" : "not_eligible";
        String route = (fc.contains("S") || fc.contains("PART")) ? "functional_correspondence"
                : sg.contains("S") ? "canonical_signal" : "none";
        List<String> used = new ArrayList<>();
        if (!classification.equals("not_eligible")) {
            if (fc.contains("S") || fc.contains("PART")) {
                used.add("functional_correspondence");
            }
            if (sg.contains("S") || sg.contains("PART")) {
                used.add("canonical_signal");
            }
        }
        Map<String, String> want = new LinkedHashMap<>();
        want.put("heta_name", hetaName == null || hetaName.isEmpty() ? decision.getOrDefault("heta_name", "") : hetaName);
        want.put("classification", classification);
        want.put("decision_route", route);
        want.put("used_indications", used.isEmpty() ? Prompt1Tsv.EMPTY : String.join(",", used));
        List<String> notes = new ArrayList<>();
        want.forEach((key, value) -> {
            String given = decision.get(key);
            if (given != null && !given.equals(value) && !given.equals(Prompt1Tsv.EMPTY) && !given.isEmpty()) {
                notes.add(heta + " " + key + ": '" + given + "' → " + value);
            }
        });
        Map<String, String> row = new LinkedHashMap<>(decision);
        row.putAll(want);
        return new Decision(row, notes);
    }

    /** The HETA name from the text of KEY R3:&lt;heta&gt;.2 ("KEY: R3:&lt;heta&gt;.2 | HETA &lt;ref&gt; &lt;name&gt; | Functional Role"). */
    static String r3HetaName(String heta, String keyText) {
        if (keyText == null) {
            return "";
        }
        Matcher m = Pattern.compile("^KEY: R3:" + Pattern.quote(heta) + "\\.2 \\| HETA " + Pattern.quote(heta)
                + " (.+?) \\| Functional Role", Pattern.MULTILINE).matcher(keyText);
        return m.find() ? m.group(1).strip() : "";
    }

    // ---- Step 13: B2 by the fixed matrix (15.1, 15.2) --------------------------------------------------------

    /** The B2 files the backend writes in the answer format of turn D, and its notes. */
    public record B2(String needs, String coverage, List<String> notes) {
        /** The two blocks as turn D's answer. */
        public String answer() {
            return "```b2_needs.tsv\n" + needs + "\n```\n\n```b2_coverage.tsv\n" + coverage + "\n```";
        }
    }

    /**
     * The B2 mapping matrix of doc 07 v9 as canonical_key → 0–2 questionnaires. A questionnaire that
     * is not in the B2 order stops the run.
     */
    static Map<String, List<String>> b2Matrix(String text, List<String> b2Order) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        List<String> bad = new ArrayList<>();
        for (Map<String, String> row : Prompt1Tsv.rows(text)) {
            List<String> qs = new ArrayList<>();
            for (String column : List.of("qest_json_1", "qest_json_2")) {
                String q = row.getOrDefault(column, "").strip();
                if (!q.isEmpty() && !q.equals(Prompt1Tsv.EMPTY) && !q.equals("-")) {
                    qs.add(q);
                }
            }
            String key = row.getOrDefault("canonical_key", "").strip();
            qs.stream().filter(q -> !b2Order.contains(q)).forEach(q -> bad.add(key + ": " + q));
            out.put(key, qs);
        }
        if (!bad.isEmpty()) {
            throw new IllegalStateException("07_B2_matrix.tsv: questionnaire(s) not in doc 07 rows 1–9 and 11–20: "
                    + String.join("; ", bad));
        }
        return out;
    }

    /**
     * Doc 07 v9 B2 MAPPING RULE (SM 15.1 and 15.2 by the backend). One need per own contribution of
     * an A3.indirect HETA whose cell is PART (unknown_content = the cell's gap). A HETA that is
     * indirect only through a signal gets one need per applicable contribution, with the fixed text
     * {@code neNeed}. Each need receives the questionnaires the matrix assigns to its canonical_key;
     * coverage has one row per need × questionnaire, in doc 07 order.
     */
    static B2 b2FromMatrix(Prompt1TurnConfig config, String decisions, String candidates, String canon, String cells,
                           Map<String, List<String>> matrix) {
        List<String> order = Prompt1Tsv.candidateOrder(candidates);
        Map<String, Map<String, String>> dec = Prompt1Tsv.byId(decisions, "heta_ref");
        List<Map<String, String>> canonRows = Prompt1Tsv.rows(canon);
        Map<String, Integer> pos = new HashMap<>();
        Map<String, String> ckey = new LinkedHashMap<>();
        for (int i = 0; i < canonRows.size(); i++) {
            Map<String, String> c = canonRows.get(i);
            String id = c.get("heta_ref") + "|" + c.get("canonical_id");
            pos.put(id, i);
            ckey.put(id, c.getOrDefault("canonical_key", "").strip());
        }
        List<Map<String, String>> cellRows = Prompt1Tsv.rows(cells);
        List<String> notes = new ArrayList<>();
        List<String[]> needs = new ArrayList<>();
        List<List<String>> needQuestionnaires = new ArrayList<>();
        List<String> needKeys = new ArrayList<>();
        for (String h : order) {
            if (!"indirect".equals(dec.getOrDefault(h, Map.of()).getOrDefault("classification", "").strip())) {
                continue;
            }
            List<Map<String, String>> hc = cellRows.stream().filter(c -> h.equals(c.get("heta_ref"))).toList();
            List<Map<String, String>> part = new ArrayList<>(hc.stream()
                    .filter(c -> "functional_correspondence".equals(c.get("route")) && "PART".equals(c.get("state").strip()))
                    .toList());
            part.sort(Comparator.comparingInt(c -> pos.getOrDefault(h + "|" + c.get("canonical_id"), 1_000_000)));
            List<String[]> rows = new ArrayList<>();
            if (!part.isEmpty()) {
                for (Map<String, String> c : part) {
                    String gap = c.get("gap").strip();
                    rows.add(new String[]{c.get("canonical_id"), c.get("meaning_ids"), c.get("link_ids"),
                            gap.isEmpty() ? Prompt1Tsv.EMPTY : gap});
                }
            } else {
                List<Map<String, String>> sig = hc.stream()
                        .filter(c -> "canonical_signal".equals(c.get("route")) && "S".equals(c.get("state").strip())).toList();
                Function<String, String> ids = column -> {
                    LinkedHashSet<String> all = new LinkedHashSet<>();
                    sig.forEach(c -> all.addAll(Prompt1Tsv.commaList(c.get(column))));
                    return all.isEmpty() ? Prompt1Tsv.EMPTY : String.join(",", all);
                };
                List<Map<String, String>> contributions = canonRows.stream()
                        .filter(c -> h.equals(c.get("heta_ref")) && "contribution".equals(c.get("kind"))).toList();
                List<String> cids = contributions.stream().filter(c -> "yes".equals(c.get("applicable").strip()))
                        .map(c -> c.get("canonical_id")).toList();
                if (cids.isEmpty()) {
                    cids = contributions.stream().map(c -> c.get("canonical_id")).toList();
                }
                for (String cid : cids) {
                    rows.add(new String[]{cid, ids.apply("meaning_ids"), ids.apply("link_ids"), config.neNeed()});
                }
                notes.add(h + ": indirect via signaal, geen PART-bijdrage; behoefte op "
                        + (cids.isEmpty() ? Prompt1Tsv.EMPTY : String.join(", ", cids)));
                if (cids.isEmpty()) {  // no own contribution in the primary-IE block: one need, no matrix entry
                    rows = new ArrayList<>();
                    rows.add(new String[]{sig.isEmpty() ? Prompt1Tsv.EMPTY : sig.get(0).get("canonical_id"),
                            ids.apply("meaning_ids"), ids.apply("link_ids"), config.neNeed()});
                }
            }
            List<String> hkeys = ckey.entrySet().stream()
                    .filter(e -> e.getKey().startsWith(h + "|") && e.getValue().contains(".4:")).map(e -> e.getValue()).toList();
            for (String[] row : rows) {
                String cid = row[0];
                String key = cid.startsWith("C") ? ckey.get(h + "|" + cid) : (hkeys.isEmpty() ? "" : hkeys.get(0));
                if (key != null && !key.isEmpty() && !matrix.containsKey(key)) {
                    notes.add(h + " " + cid + ": " + key + " staat niet in 07_B2_matrix.tsv; geen vragenlijst");
                }
                String mids = row[1] == null || row[1].isEmpty() ? Prompt1Tsv.EMPTY : row[1];
                String lids = row[2] == null || row[2].isEmpty() ? Prompt1Tsv.EMPTY : row[2];
                needs.add(new String[]{h, cid, mids, lids, row[3]});
                needQuestionnaires.add(matrix.getOrDefault(key == null ? "" : key, List.of()));
                needKeys.add(key == null ? "None" : key);
            }
        }
        StringBuilder needsText = new StringBuilder(config.headers().get("b2_needs.tsv"));
        StringBuilder coverageText = new StringBuilder(config.headers().get("b2_coverage.tsv"));
        for (int i = 0; i < needs.size(); i++) {
            String gap = String.format("GAP_%03d", i + 1);
            String[] n = needs.get(i);
            List<String> qs = needQuestionnaires.get(i);
            String dims = qs.isEmpty() ? Prompt1Tsv.EMPTY
                    : String.join(" ;; ", qs.stream().map(q -> config.qScope().get(q)).toList());
            needsText.append('\n').append(String.join("\t", gap, n[0], n[1], n[2], n[3], n[4], dims));
            for (String q : config.b2Order()) {
                if (qs.contains(q)) {
                    coverageText.append('\n').append(String.join("\t", gap, q, "yes", config.qScope().get(q),
                            Prompt1Tsv.EMPTY, "07_B2_matrix: " + needKeys.get(i)));
                } else {
                    coverageText.append('\n').append(String.join("\t", gap, q, "no", Prompt1Tsv.EMPTY, Prompt1Tsv.EMPTY,
                            Prompt1Tsv.EMPTY));
                }
            }
        }
        return new B2(needsText.toString(), coverageText.toString(), notes);
    }

    /**
     * SM 15.1 numbering: GAP_001, GAP_002, … in A3.indirect (candidate) order, then source-clause
     * order; the model's (or the matrix's) own ids are mapped in needs and coverage, and coverage rows
     * are put in GAP × doc 07 order.
     */
    static B2 numberGaps(Prompt1TurnConfig config, String candidates, String canon, String needs, String coverage) {
        List<String> order = Prompt1Tsv.candidateOrder(candidates);
        Map<String, Integer> clause = new HashMap<>();
        List<Map<String, String>> canonRows = Prompt1Tsv.rows(canon);
        for (int i = 0; i < canonRows.size(); i++) {
            clause.put(canonRows.get(i).get("heta_ref") + "|" + canonRows.get(i).get("canonical_id"), i);
        }
        String headNeeds = config.headers().get("b2_needs.tsv");
        String headCoverage = config.headers().get("b2_coverage.tsv");
        List<Map<String, String>> needRows = Prompt1Tsv.rows(needs);
        List<Map<String, String>> coverageRows = Prompt1Tsv.rows(coverage);
        if (needRows.isEmpty()) {
            return new B2(needs, coverage, List.of());
        }
        List<Integer> index = new ArrayList<>();
        for (int i = 0; i < needRows.size(); i++) {
            index.add(i);
        }
        index.sort(Comparator.<Integer>comparingInt(i -> {
                    int at = order.indexOf(needRows.get(i).get("heta_ref"));
                    return at < 0 ? 99 : at;
                })
                .thenComparingInt(i -> clause.getOrDefault(needRows.get(i).get("heta_ref") + "|"
                        + needRows.get(i).get("canonical_id"), 1_000_000))
                .thenComparing(i -> needRows.get(i).get("canonical_id"), (a, b) -> compareCanonicalId(a, b))
                .thenComparingInt(i -> i));
        List<String> ids = needRows.stream().map(r -> r.get("gap_id")).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalStateException("b2_needs.tsv: a gap_id occurs twice; give every need its own gap_id");
        }
        Map<String, String> renamed = new LinkedHashMap<>();
        for (int k = 0; k < index.size(); k++) {
            renamed.put(needRows.get(index.get(k)).get("gap_id"), String.format("GAP_%03d", k + 1));
        }
        TreeSet<String> unknown = new TreeSet<>();
        coverageRows.stream().map(c -> c.get("gap_id")).filter(g -> !renamed.containsKey(g)).forEach(x -> unknown.add(x));
        if (!unknown.isEmpty()) {
            throw new IllegalStateException("b2_coverage.tsv: gap_id(s) not in b2_needs.tsv: " + String.join(", ", unknown));
        }
        List<String> notes = new ArrayList<>();
        renamed.forEach((from, to) -> {
            if (!from.equals(to)) {
                notes.add(from + " → " + to);
            }
        });
        List<String> needColumns = List.of(headNeeds.split("\t", -1));
        StringBuilder outNeeds = new StringBuilder(headNeeds);
        for (int i : index) {
            Map<String, String> r = needRows.get(i);
            outNeeds.append('\n').append(String.join("\t", needColumns.stream()
                    .map(k -> k.equals("gap_id") ? renamed.get(r.get("gap_id")) : r.get(k)).toList()));
        }
        Map<String, Integer> gapPosition = new HashMap<>();
        for (int k = 0; k < index.size(); k++) {
            gapPosition.put(renamed.get(needRows.get(index.get(k)).get("gap_id")), k);
        }
        List<Map<String, String>> sortedCoverage = new ArrayList<>(coverageRows);
        sortedCoverage.sort(Comparator.<Map<String, String>>comparingInt(c -> gapPosition.get(renamed.get(c.get("gap_id"))))
                .thenComparingInt(c -> {
                    int at = config.b2Order().indexOf(c.get("qest_json"));
                    return at < 0 ? 99 : at;
                }));
        List<String> coverageColumns = List.of(headCoverage.split("\t", -1));
        StringBuilder outCoverage = new StringBuilder(headCoverage);
        for (Map<String, String> c : sortedCoverage) {
            outCoverage.append('\n').append(String.join("\t", coverageColumns.stream()
                    .map(k -> k.equals("gap_id") ? renamed.get(c.get("gap_id")) : c.get(k)).toList()));
        }
        return new B2(outNeeds.toString(), outCoverage.toString(), notes);
    }

    /** Jan's sort key for a canonical_id: own contributions (C…) first, then by number. */
    private static int compareCanonicalId(String a, String b) {
        boolean ca = a != null && a.startsWith("C");
        boolean cb = b != null && b.startsWith("C");
        if (ca != cb) {
            return ca ? -1 : 1;
        }
        return Integer.compare(Prompt1Tsv.digits(a), Prompt1Tsv.digits(b));
    }

    // ---- Steps 12, 13 (15.3) and 14 --------------------------------------------------------------------------

    /** The answer of an intake observation (source_value is {"question": …, "answer": …}), else the value itself. */
    static String obsAnswer(Map<String, String> observation) {
        String value = observation.getOrDefault("source_value", "");
        try {
            JsonNode node = MAPPER.readTree(value);
            if (node != null && node.isObject() && node.has("answer")) {
                JsonNode answer = node.get("answer");
                return answer.isNull() ? "" : answer.isTextual() ? answer.asText() : answer.toString();
            }
        } catch (Exception e) {
            // not JSON: the value itself
        }
        return value;
    }

    /** Fully elapsed years between a date of birth and the run date (Jan's {@code age_on}). */
    static int ageOn(String dob, String runDate) {
        LocalDate birth = parseDate(dob.strip().replaceAll("(\\b[A-Za-z]{3})\\.", "$1"));
        if (birth == null) {
            throw new IllegalStateException("Step 12: date of birth not readable");
        }
        LocalDate run = LocalDate.parse(runDate);
        int age = run.getYear() - birth.getYear();
        if (run.getMonthValue() < birth.getMonthValue()
                || (run.getMonthValue() == birth.getMonthValue() && run.getDayOfMonth() < birth.getDayOfMonth())) {
            age--;
        }
        return age;
    }

    /** "%d %b %Y", "%d %B %Y" (English or Dutch month names), "%Y-%m-%d", "%d-%m-%Y" or "%d/%m/%Y". */
    private static LocalDate parseDate(String text) {
        try {
            String[] words = text.split("\\s+");
            if (words.length == 3) {
                Integer month = MONTHS.get(words[1].toLowerCase(Locale.ROOT));
                if (month != null) {
                    return LocalDate.of(Integer.parseInt(words[2]), month, Integer.parseInt(words[0]));
                }
            }
            Matcher iso = Pattern.compile("(\\d{4})-(\\d{1,2})-(\\d{1,2})").matcher(text);
            if (iso.matches()) {
                return LocalDate.of(Integer.parseInt(iso.group(1)), Integer.parseInt(iso.group(2)), Integer.parseInt(iso.group(3)));
            }
            Matcher dmy = Pattern.compile("(\\d{1,2})[-/](\\d{1,2})[-/](\\d{4})").matcher(text);
            if (dmy.matches()) {
                return LocalDate.of(Integer.parseInt(dmy.group(3)), Integer.parseInt(dmy.group(2)), Integer.parseInt(dmy.group(1)));
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /** qest_json → label from a doc 07 table, without bold marks (linearized rows and markdown rows). */
    static Map<String, String> qLabels(String keyText) {
        Map<String, String> out = new LinkedHashMap<>();
        if (keyText == null) {
            return out;
        }
        Matcher m = LINEAR_ROW.matcher(keyText);
        while (m.find()) {
            out.put(m.group(3), m.group(2).replace("*", "").replaceAll("\\s+", " ").strip());
        }
        for (String line : keyText.split("\n")) {
            String trimmed = Prompt1Tsv.strip(line.strip(), "|");
            String[] cells = trimmed.split("\\|", -1);
            if (cells.length >= 3 && NUMBER_CELL.matcher(cells[0].strip()).matches()) {
                out.put(Prompt1Tsv.strip(cells[2].strip(), "*` "), cells[1].strip().replace("*", "").replaceAll("\\s+", " ").strip());
            }
        }
        return out;
    }

    /**
     * SM Step 12: trigger = intake 1.7 answered "ja" or a question of sections 2.1–2.8 answered "ja";
     * the variant by the fully elapsed years on CURRENT_RUN_DATE (0–7 child, 8–17 hybride, 18+ adult);
     * the label from KEY 07:B1.
     */
    static ArrayNode b1Items(String observations, String runDate, String b1Key) {
        ArrayNode items = JsonNodeFactory.instance.arrayNode();
        List<Map<String, String>> obs = Prompt1Tsv.rows(observations);
        boolean triggered = obs.stream().anyMatch(o -> ("1.7".equals(o.get("source_label"))
                || SECTION_2.matcher(o.getOrDefault("source_label", "")).matches())
                && obsAnswer(o).strip().toLowerCase(Locale.ROOT).startsWith("ja"));
        if (!triggered) {
            return items;
        }
        String dob = obs.stream().filter(o -> "date_of_birth".equals(o.get("source_label")))
                .map(o -> o.get("source_value")).findFirst().orElse("");
        if (dob.isEmpty() || runDate == null || runDate.isEmpty()) {
            throw new IllegalStateException("Step 12: date of birth or CURRENT_RUN_DATE missing");
        }
        int age = ageOn(Prompt1Tsv.strip(dob, "\""), runDate);
        String q = age <= 7 ? "ethos-scan-child" : age <= 17 ? "ethos-scan-hybride" : "ethos-scan-adult";
        String label = qLabels(b1Key).get(q);
        if (label == null) {
            throw new IllegalStateException("Step 12: " + q + " not found in KEY 07:B1");
        }
        ObjectNode item = items.addObject();
        item.put("nr", 1);
        item.put("label", label);
        item.put("ID", q);
        item.put("qest_json", q);
        return items;
    }

    /** SM 15.3 (doc 07 B2 row 10): sum of intake scores 5.8–5.10 ≥ 6, or 5.8 ≥ 3; a missing or non-numeric score gives none. */
    static boolean ysqTrigger(String observations) {
        Map<String, String> scores = new HashMap<>();
        for (Map<String, String> o : Prompt1Tsv.rows(observations)) {
            String label = o.getOrDefault("source_label", "");
            if (List.of("5.8", "5.9", "5.10").contains(label)) {
                scores.put(label, obsAnswer(o));
            }
        }
        try {
            double a = Double.parseDouble(scores.get("5.8").strip().replace(',', '.'));
            double b = Double.parseDouble(scores.get("5.9").strip().replace(',', '.'));
            double c = Double.parseDouble(scores.get("5.10").strip().replace(',', '.'));
            return a + b + c >= 6 || a >= 3;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- the final JSON ----------------------------------------------------------------------------------

    /** The final JSON and the notes of its assembly. */
    public record Output(ObjectNode json, List<String> notes) {
    }

    /**
     * The final JSON (schema v0.46d-lean) built from the closed working files (Jan's
     * {@code assemble_output}).
     *
     * @param file returns a working file's text, or "" when it is absent
     * @param key  returns the text of a source key, or null when it is not found
     */
    static Output assembleOutput(Prompt1TurnConfig config, Function<String, String> file, String runId, String runDate,
                                 Function<String, String> key) {
        JsonNodeFactory f = JsonNodeFactory.instance;
        List<String> notes = new ArrayList<>();
        Map<String, String> tasks = new HashMap<>();
        rows(file, "tasks.tsv").forEach(t -> tasks.put(t.get("step"), t.get("status")));
        ObjectNode stage = f.objectNode();
        int last = -1;
        boolean open = false;
        for (int i = 0; i < 15; i++) {
            boolean closed = "closed".equals(tasks.get(String.valueOf(i))) || i >= 9;
            stage.put("step_" + i, closed ? "closed" : "unclosed");
            if (!open && closed) {
                last = i;
            } else {
                open = true;
            }
        }
        stage.put("eindcontrole", "closed");

        Map<String, Map<String, String>> dirs = Prompt1Tsv.byId(file.apply("directions.tsv"), "direction_id");
        ArrayNode ftfs = f.arrayNode();
        for (Map<String, String> p : rows(file, "priority.tsv")) {
            String direction = p.get("direction_id");
            Map<String, String> d = dirs.getOrDefault(direction, Map.of());
            ObjectNode ftf = ftfs.addObject();
            ftf.put("ftf_id", Prompt1Tsv.ftfOf(direction));
            ftf.put("source_direction_id", direction);
            ftf.put("identity", d.getOrDefault("identity", ""));
            ftf.put("functional_directionality", d.getOrDefault("functional_directionality", ""));
            ftf.put("priority_status", p.get("priority_status").strip());
            ftf.put("priority_basis", p.get("basis"));
        }

        List<Map<String, String>> selection = rows(file, "a1_selection.tsv");
        Map<String, String> primary = selection.stream()
                .filter(r -> r.get("step").startsWith("7.2D") && "primary".equals(r.get("finding").strip()))
                .findFirst().orElseThrow(() -> new IllegalStateException("a1_selection.tsv: no 7.2D primary row"));
        List<String> secondaries = new ArrayList<>(selection.stream()
                .filter(r -> r.get("step").startsWith("7.3") && "secondary".equals(r.get("finding").strip()))
                .map(r -> r.get("ie_ref")).toList());
        // v07: the IE titles come from the header of KEY R4:<ie>:01 (not from A2), read primary first, then the
        // secondaries in a1_selection.tsv order, as Jan's backend does (the order of the retrieval log)
        Map<String, String> titles = new HashMap<>();
        titles.put(primary.get("ie_ref"), ieTitle(key.apply("R4:" + primary.get("ie_ref") + ":01")));
        secondaries.forEach(ie -> titles.put(ie, ieTitle(key.apply("R4:" + ie + ":01"))));
        secondaries.sort(Comparator.comparingInt(ie -> config.ies().contains(ie) ? config.ies().indexOf(ie) : 99));
        Map<String, Map<String, String>> candidates = Prompt1Tsv.byId(file.apply("a1_ie_candidates.tsv"), "ie_ref");
        List<Map<String, String>> membership = rows(file, "ie_membership.tsv");
        Function<String, ObjectNode> ieSelection = ie -> {
            ObjectNode entry = f.objectNode();
            entry.put("ie_ref", ie);
            entry.put("ie_title", titles.getOrDefault(ie, ""));
            ArrayNode represented = entry.putArray("represented_ftf_ids");
            if (candidates.containsKey(ie)) {
                Prompt1Tsv.commaList(candidates.get(ie).get("represented_ftf_ids")).forEach(x -> represented.add(x));
            } else {
                membership.stream().filter(r -> ie.equals(r.get("ie_ref"))).map(r -> r.get("ftf_id")).distinct()
                        .forEach(x -> represented.add(x));
            }
            return entry;
        };
        ObjectNode a1 = f.objectNode();
        a1.set("primary_ie", ieSelection.apply(primary.get("ie_ref")));
        ArrayNode secondaryIe = a1.putArray("secondary_ie");
        secondaries.forEach(ie -> secondaryIe.add(ieSelection.apply(ie)));
        a1.put("primary_selection_basis", primary.get("basis"));

        List<String> order = Prompt1Tsv.candidateOrder(file.apply("a3_candidates.tsv"));
        Map<String, Map<String, String>> decisions = Prompt1Tsv.byId(file.apply("a3_decisions.tsv"), "heta_ref");
        ArrayNode a3 = f.arrayNode();
        List<String> direct = new ArrayList<>();
        List<String> directOrIndirect = new ArrayList<>();
        for (String h : order) {
            Map<String, String> d = decisions.get(h);
            if (d == null) {
                throw new IllegalStateException("a3_decisions.tsv has no row for candidate " + h);
            }
            ObjectNode entry = a3.addObject();
            entry.put("heta_ref", h);
            entry.put("heta_name", d.get("heta_name"));
            entry.put("classification", d.get("classification"));
            entry.put("decision_route", d.get("decision_route"));
            ArrayNode used = entry.putArray("used_indications");
            Prompt1Tsv.commaList(d.get("used_indications")).forEach(x -> used.add(x));
            ArrayNode decisive = entry.putArray("decisive_meaning_ids");
            Prompt1Tsv.commaList(d.get("decisive_meaning_ids")).forEach(x -> decisive.add(x));
            entry.put("basis", d.get("basis"));
            String classification = d.get("classification");
            if ("direct".equals(classification)) {
                direct.add(h);
            }
            if ("direct".equals(classification) || "indirect".equals(classification)) {
                directOrIndirect.add(h);
            }
        }

        String observations = file.apply("observations.tsv");
        ArrayNode b1 = b1Items(observations, runDate, Objects.requireNonNullElse(key.apply("07:B1"), ""));

        List<Map<String, String>> needs = rows(file, "b2_needs.tsv");
        List<Map<String, String>> coverage = rows(file, "b2_coverage.tsv");
        Map<String, String> labels = qLabels(Objects.requireNonNullElse(key.apply("07:B2"), ""));
        List<String> chosen = new ArrayList<>(config.b2Order().stream().filter(q -> coverage.stream()
                .anyMatch(c -> q.equals(c.get("qest_json")) && "yes".equals(c.get("covers").strip()))).toList());
        if (ysqTrigger(observations)) {
            chosen.add("ysq");
        }
        List<String> missing = chosen.stream().filter(q -> !labels.containsKey(q)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Step 13: no label in KEY 07:B2 for " + String.join(", ", missing)
                    + " (the questionnaires of rows 11–20 exist from doc 07 v9 on; load doc 07 v9 into the knowledge tables)");
        }
        Set<String> covered = new HashSet<>();
        coverage.stream().filter(c -> "yes".equals(c.get("covers").strip())).forEach(c -> covered.add(c.get("gap_id")));
        ObjectNode b2 = f.objectNode();
        ArrayNode needList = b2.putArray("needs");
        for (Map<String, String> n : needs) {
            ObjectNode need = needList.addObject();
            for (String k : List.of("gap_id", "heta_ref", "canonical_id", "unknown_content")) {
                need.put(k, n.get(k));
            }
        }
        ArrayNode items = b2.putArray("items");
        for (int i = 0; i < chosen.size(); i++) {
            ObjectNode item = items.addObject();
            item.put("nr", i + 1);
            item.put("label", labels.get(chosen.get(i)));
            item.put("ID", chosen.get(i));
            item.put("qest_json", chosen.get(i));
        }
        ArrayNode uncovered = b2.putArray("uncovered_gap_ids");
        needs.stream().map(n -> n.get("gap_id")).filter(g -> !covered.contains(g)).forEach(x -> uncovered.add(x));

        // v07: the HETA_JSON_IDENTIFIER of every direct and indirect HETA (production A3), from KEY 06:<heta_ref>
        ObjectNode hetaJson = f.objectNode();
        for (String h : directOrIndirect) {
            Matcher json = HETA_JSON_IDENTIFIER.matcher(Objects.requireNonNullElse(key.apply("06:" + h), ""));
            if (!json.find()) {
                throw new IllegalStateException("Step 14: HETA_JSON_IDENTIFIER not found in KEY 06:" + h);
            }
            hetaJson.put(h, json.group(1));
        }

        ArrayNode b3 = f.arrayNode();
        for (String h : direct) {
            String record = Objects.requireNonNullElse(key.apply("06:" + h), "");
            Matcher id = HETA_ID.matcher(record);
            Matcher json = HETA_JSON_IDENTIFIER.matcher(record);
            Matcher ref = HETA_APPENDIX_REF.matcher(record);
            boolean hasId = id.find();
            boolean hasJson = json.find();
            if (!hasId || !hasJson || (ref.find() && !ref.group(1).equals(h))) {
                throw new IllegalStateException("Step 14: HETA_ID, HETA_JSON_IDENTIFIER or HETA_APPENDIX_REF " + h
                        + " not found in KEY 06:" + h);
            }
            String module = key.apply("05:" + json.group(1));
            if (module == null || !module.contains(json.group(1))) {
                notes.add("B3: " + h + " (" + json.group(1) + ") has no module in doc 05; no item");
                continue;
            }
            ObjectNode item = b3.addObject();
            item.put("nr", b3.size());
            item.put("heta_ref", h);
            item.put("heta_id", id.group(1).strip());
            item.put("heta_json", json.group(1));
            item.put("tier", "High");
        }

        ObjectNode out = f.objectNode();
        out.put("prototype_version", "0.46d-lean");
        out.put("run_id", runId);
        out.put("current_run_date", runDate);
        out.put("stop_after_step", 14);
        out.put("validation", "pending");
        out.put("run_status", last == 14 ? "valid" : "invalid");
        out.put("failure_stage", "");
        out.put("failure_reason", "");
        out.put("completed_through_step", last);
        out.set("stage_status", stage);
        ObjectNode worksheets = out.putObject("worksheets");
        WORKSHEETS.forEach(w -> worksheets.put(w, w + ".tsv"));
        ArrayNode manifest = out.putArray("client_source_manifest");
        for (Map<String, String> m : rows(file, "manifest.tsv")) {
            ObjectNode source = manifest.addObject();
            source.put("source_id", m.get("source_id"));
            source.put("source_name", m.get("source_name"));
            source.put("source_type", m.get("source_type"));
            source.put("source_order", Integer.parseInt(m.get("source_order").strip()));
        }
        out.set("ftfs", ftfs);
        out.set("a1", a1);
        out.set("a3", a3);
        out.set("heta_json", hetaJson);
        out.set("b1", b1);
        out.set("b2", b2);
        out.set("b3", b3);
        return new Output(out, notes);
    }

    private static final Pattern R4_TITLE = Pattern.compile("KEY: R4:\\S+ \\| IE \\S+ (.+?) \\|");

    /** v07 {@code ie_title}: the IE title from the header line of KEY R4:&lt;ie&gt;:01 ("KEY: R4:… | IE 22.7 &lt;title&gt; | …"). */
    static String ieTitle(String r4Text) {
        String first = Objects.requireNonNullElse(r4Text, "").strip().lines().filter(line -> !line.isBlank())
                .findFirst().orElse("");
        Matcher m = R4_TITLE.matcher(first);
        return m.lookingAt() ? m.group(1).strip() : "";
    }

    private static List<Map<String, String>> rows(Function<String, String> file, String name) {
        String text = file.apply(name);
        return text == null || text.isEmpty() ? List.of() : Prompt1Tsv.rows(text);
    }

}
