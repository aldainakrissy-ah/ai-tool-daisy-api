package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1ExperimentService.IntakeFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The deterministic completeness and rule check of a Prompt 1 run, ported from Jan's
 * {@code validate_run.py} (release v06). Problems are errors that break a later step: the runner sends
 * them back to the model (repair turn or correction). Warnings are form slips and missing explanations;
 * they are reported, never sent to the model. Only steps up to {@code stop} are checked. Messages are
 * Jan's (Dutch), so his notes and ours can be compared. It does not judge whether a finding is correct.
 */
public final class Prompt1RunCheck {

    /** The problems (errors) and warnings of one check. */
    public record Result(List<String> problems, List<String> warnings) {
    }

    private static final List<String> STEPS = new ArrayList<>();

    static {
        for (int i = 0; i < 15; i++) {
            STEPS.add(String.valueOf(i));
        }
        STEPS.add("eindcontrole");
    }

    private static final List<String> HETA_SECTIONS = List.of(".2", ".4", ".4:{prim}", ".4:SIG", ".5.2");
    /** Step → the keys the SM requires on every run (SM 0.3): the keys turn A1 reads for Steps 1 and 2. */
    private static final Map<Integer, List<String>> REQUIRED_KEY_READS = Map.of(
            1, Prompt1TurnMessages.A1_STEP1_KEYS, 2, Prompt1TurnMessages.A1_STEP2_KEYS);
    private static final Set<String> SERVER_FILES = Set.of("usage.tsv", "retrieval_log.tsv", "manifest.tsv",
            "observations.tsv", "observation_audit.tsv", "ie_membership.tsv", "a1_ie_candidates.tsv");
    private static final Set<String> MODEL_FILES = Set.of("tasks.tsv", "meanings.tsv", "links.tsv", "grouping.tsv",
            "directions.tsv", "cts_admission.tsv", "priority.tsv", "ie_core_filter.tsv", "ie_ftf_cells.tsv",
            "a1_selection.tsv", "heta_canon.tsv", "heta_cells.tsv", "b2_needs.tsv", "b2_coverage.tsv",
            "a3_candidates.tsv", "a3_evidence_basis.tsv", "a3_decisions.tsv");
    private static final Pattern ELEM_ROW = Pattern.compile(
            "-\\s*(\\d+\\.\\d+-A(\\d+)-([EQC])\\d+)\\s*\\|\\s*(\\w+)\\s*\\|\\s*(required|not required)");
    private static final Pattern OVERLAP = Pattern.compile("(M\\w+)\\s*(?:→|->)\\s*(FD_\\d+)\\s*\\(([^)]*)\\)");
    private static final Pattern GROUND = Pattern.compile("\\bG[1-4]\\b");
    private static final Pattern ELEMENT_IDS_ONLY = Pattern.compile("[\\d.]+-A\\d+-[EQC]\\d+(\\s*,\\s*[\\d.]+-A\\d+-[EQC]\\d+)*");
    private static final Pattern MEANING = Pattern.compile("M\\d+[a-z]?");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<String> problems = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();

    private Prompt1RunCheck() {
    }

    /**
     * Checks a run up to a step.
     *
     * @param files            the run's working files (and output_controle.json and output.json once turn D wrote them)
     * @param stop             the last step to check; null = the control JSON's stop_after_step, else tasks.tsv
     * @param intake           the run's intake files (personal data and intake questionnaire), for Steps 12–13
     * @param productionSchema the JSON Schema of the production output.json (v07)
     * @param key              the text of a source key ("KEY: …" up to the next key), or null when not found
     */
    public static Result check(Prompt1TurnConfig config, Map<String, String> files, Integer stop, List<IntakeFile> intake,
                               JsonNode productionSchema, Function<String, String> key) {
        Prompt1RunCheck c = new Prompt1RunCheck();
        c.run(config, files, stop, intake, productionSchema, key);
        return new Result(List.copyOf(c.problems), List.copyOf(c.warnings));
    }

    /**
     * The problems this turn added: those of the check up to the turn's last step, without the problems
     * already present at the check of the turn before (Jan's {@code baseline}/{@code check}). The lines
     * Jan's runner filters out ("output.json ontbreekt", "na STOP_AFTER_STEP") are left out too.
     */
    public static List<String> newProblems(Result before, Result after) {
        Set<String> old = before == null ? Set.of() : new HashSet<>(before.problems());
        return after.problems().stream().filter(p -> !old.contains(p) && !p.contains("output.json ontbreekt")
                && !p.contains("na STOP_AFTER_STEP")).toList();
    }

    private void bad(String message) {
        problems.add(message);
    }

    private void warn(String message) {
        warnings.add(message);
    }

    private static List<String> ids(String cell) {
        return Prompt1Tsv.ids(cell);
    }

    private static String get(Map<String, String> row, String column) {
        String value = row == null ? null : row.get(column);
        return value == null ? "" : value;
    }

    private List<Map<String, String>> tsv(Map<String, String> files, String name, boolean required) {
        String text = files.get(name);
        if (text == null) {
            if (required) {
                bad("ontbrekend werkbestand: " + name);
            }
            return List.of();
        }
        return Prompt1Tsv.rows(text);
    }

    private static String sourceText(Map<String, String> o) {
        String value = get(o, "source_value");
        if ("question_response".equals(o.get("record_type"))) {
            try {
                JsonNode answer = MAPPER.readTree(value).get("answer");
                return answer == null || answer.isNull() ? "" : answer.isTextual() ? answer.asText() : answer.toString();
            } catch (Exception e) {
                return value;
            }
        }
        return value;
    }

    private static String loose(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[\\W_]+", "");
    }

    /** Literal in a source: fine. Equal after ignoring capitals, spaces and punctuation: warning. Otherwise: problem. */
    private void excerptCheck(String label, String excerpt, List<String> texts) {
        if (texts.stream().anyMatch(x -> x.contains(excerpt))) {
            return;
        }
        String l = loose(excerpt);
        if (!l.isEmpty() && texts.stream().anyMatch(x -> loose(x).contains(l))) {
            warn(label + ": excerpt wijkt alleen in hoofdletters, spaties of leestekens af van de bron");
        } else {
            bad(label + ": excerpt niet in de bron");
        }
    }

    /** "KEY: 02:B.2 Process Thinking" → "02:B.2". */
    private static String keyOf(String query) {
        String q = query.strip();
        if (!q.startsWith("KEY:") || q.substring(4).isBlank()) {
            return null;
        }
        return WHITESPACE.split(q.substring(4).strip())[0];
    }

    /**
     * validate_run.py v07: the production JSON (output.json) conforms to the schema and agrees with the control JSON:
     * the same primary and secondary IEs, B1/B2 questionnaires, B3 modules and number of direct/indirect HETAs.
     */
    private void checkProduction(Map<String, String> files, JsonNode out, JsonNode productionSchema) {
        if (!files.containsKey("output.json")) {
            bad("output.json (production) ontbreekt naast output_controle.json");
            return;
        }
        try {
            JsonNode prod = MAPPER.readTree(files.get("output.json"));
            List<String> errors = Prompt1ProductionJson.schemaErrors(prod, productionSchema);
            errors.forEach(e -> bad("production-JSON: " + e));
            if (!errors.isEmpty() || out == null) {
                return;
            }
            if (!firstWord(prod.path("A1").path("primary_ie").asText()).equals(out.path("a1").path("primary_ie").path("ie_ref").asText())) {
                bad("production-JSON: primary_ie ≠ controle");
            }
            List<String> prodSecondary = new ArrayList<>();
            prod.path("A1").path("secondary_ie").forEach(x -> prodSecondary.add(firstWord(x.asText())));
            List<String> controlSecondary = new ArrayList<>();
            out.path("a1").path("secondary_ie").forEach(x -> controlSecondary.add(x.path("ie_ref").asText()));
            if (!prodSecondary.equals(controlSecondary)) {
                bad("production-JSON: secondary_ie ≠ controle");
            }
            if (!values(prod.path("B1"), "ID").equals(values(out.path("b1"), "ID"))
                    || !values(prod.path("B2"), "ID").equals(values(out.path("b2").path("items"), "ID"))) {
                bad("production-JSON: B1/B2 ≠ controle");
            }
            if (!values(prod.path("B3"), "heta_json").equals(values(out.path("b3"), "heta_json"))) {
                bad("production-JSON: B3 ≠ controle");
            }
            for (String c : List.of("direct", "indirect")) {
                long count = 0;
                for (JsonNode a : out.path("a3")) {
                    if (c.equals(a.path("classification").asText())) {
                        count++;
                    }
                }
                if (prod.path("A3").path(c).size() != count) {
                    bad("production-JSON: A3 " + c + " ≠ controle");
                }
            }
        } catch (Exception e) {
            bad("production-JSON niet te controleren: " + e.getMessage());
        }
    }

    private static String firstWord(String text) {
        int space = text.indexOf(' ');
        return space < 0 ? text : text.substring(0, space);
    }

    private static List<String> values(JsonNode list, String field) {
        List<String> out = new ArrayList<>();
        list.forEach(x -> out.add(x.path(field).asText()));
        return out;
    }

    private void run(Prompt1TurnConfig config, Map<String, String> files, Integer stop, List<IntakeFile> intake,
                     JsonNode productionSchema, Function<String, String> key) {
        List<String> ies = config.ies();
        JsonNode out = null;
        // v07: output_controle.json is the full control JSON; output.json is the production JSON (old runs: output.json is full)
        String src = files.containsKey("output_controle.json") ? "output_controle.json" : "output.json";
        if (files.containsKey(src)) {
            try {
                out = MAPPER.readTree(files.get(src));
            } catch (Exception e) {
                bad(src + " is geen geldige JSON");
            }
            if (out != null && !out.has("a1")) {
                out = null;  // a production JSON without a control JSON: nothing in it can be checked
                bad("output.json ontbreekt (eind-JSON uit de Playground opslaan als output.json); JSON-controles overgeslagen");
            }
            if (src.equals("output_controle.json")) {
                checkProduction(files, out, productionSchema);
            }
        } else {
            bad("output.json ontbreekt (eind-JSON uit de Playground opslaan als output.json); JSON-controles overgeslagen");
        }
        for (String name : new TreeSet<>(files.keySet())) {
            if (name.endsWith(".tsv") && !name.contains("/") && !SERVER_FILES.contains(name) && !MODEL_FILES.contains(name)) {
                warn("extra werkbestand buiten doc 18: " + name);
            }
        }
        int s;
        if (out != null) {
            s = out.path("stop_after_step").asInt(14);
        } else if (stop != null) {
            s = stop;
        } else {
            s = tsv(files, "tasks.tsv", false).stream()
                    .filter(t -> get(t, "step").matches("\\d+") && !"not_started".equals(get(t, "status")))
                    .mapToInt(t -> Integer.parseInt(t.get("step"))).max().orElse(14);
        }
        final int S = s;
        java.util.function.IntPredicate req = step -> S >= step;

        // ---- tasks and retrieval (0.1, 0.3)
        List<Map<String, String>> tasks = tsv(files, "tasks.tsv", true);
        Map<String, Map<String, String>> tstat = new HashMap<>();
        tasks.forEach(t -> tstat.put(get(t, "step"), t));
        for (String step : STEPS) {
            if (!tstat.containsKey(step)) {
                bad("takenlijst: stap " + step + " ontbreekt");
                continue;
            }
            if (step.matches("\\d+") && Integer.parseInt(step) > S && !"not_started".equals(get(tstat.get(step), "status"))) {
                bad("takenlijst: stap " + step + " na STOP_AFTER_STEP is niet not_started");
            }
        }
        List<Integer> seqs = tasks.stream().filter(t -> "closed".equals(get(t, "status")))
                .sorted(Comparator.comparingInt(t -> STEPS.contains(get(t, "step")) ? STEPS.indexOf(get(t, "step")) : 99))
                .map(t -> get(t, "seq").matches("\\d+") ? Integer.parseInt(get(t, "seq")) : 0).toList();
        List<Integer> sortedSeqs = new ArrayList<>(seqs);
        Collections.sort(sortedSeqs);
        if (!seqs.equals(sortedSeqs)) {
            warn("takenlijst: stappen niet in SM-volgorde gesloten");
        }
        List<Map<String, String>> rlog = tsv(files, "retrieval_log.tsv", true);
        List<String[]> queries = rlog.stream().map(r -> new String[]{get(r, "step"), get(r, "query").strip()}).toList();
        for (Map<String, String> r : rlog) {
            String chunks = get(r, "chunk_ids").strip();
            if (chunks.isEmpty() || chunks.equals(Prompt1Tsv.EMPTY)) {
                warn("retrieval " + get(r, "seq") + ": geen chunk_ids of foutmelding (0.3: alleen echt uitgevoerde ophalingen loggen)");
            }
            if (List.of("gap", "retry").contains(get(r, "purpose")) && get(r, "gap").isBlank()) {
                warn("retrieval " + get(r, "seq") + ": gap/retry zonder benoemd gat");
            }
        }
        java.util.function.BiPredicate<String, Integer> keyReadAtStep = (k, step) -> queries.stream()
                .anyMatch(q -> k.equals(keyOf(q[1])) && (step == null || q[0].equals(String.valueOf(step))));
        java.util.function.Predicate<String> keyRead = k -> keyReadAtStep.test(k, null);
        REQUIRED_KEY_READS.forEach((step, keys) -> {
            if (req.test(step)) {
                keys.stream().filter(k -> !keyReadAtStep.test(k, step))
                        .forEach(k -> warn("stap " + step + ": verplichte ophaling KEY: " + k + " ontbreekt in retrieval_log"));
            }
        });

        // ---- step 0-1
        Map<String, Map<String, String>> obs = new LinkedHashMap<>();
        tsv(files, "observations.tsv", true).forEach(o -> obs.put(get(o, "observation_id"), o));
        obs.values().stream().filter(o -> "structured_field".equals(o.get("record_type")) && get(o, "source_value").startsWith("\""))
                .forEach(o -> warn(get(o, "observation_id") + ": string niet exact (aanhalingstekens), stap 0"));
        Map<String, Map<String, String>> means = new LinkedHashMap<>();
        tsv(files, "meanings.tsv", true).forEach(m -> means.put(get(m, "meaning_id"), m));
        Map<String, Map<String, String>> links = new LinkedHashMap<>();
        tsv(files, "links.tsv", true).forEach(l -> links.put(get(l, "link_id"), l));
        for (Map<String, String> m : means.values()) {
            List<String> carriers = ids(m.get("carrier_observation_ids"));
            if (carriers.isEmpty()) {
                carriers = List.of(get(m, "observation_id"));
            }
            if (!carriers.get(0).equals(get(m, "observation_id"))) {
                warn(get(m, "meaning_id") + ": observation_id is niet de eerste drager");
            }
            excerptCheck(get(m, "meaning_id"), get(m, "source_excerpt"), carriers.stream()
                    .filter(c -> obs.containsKey(c)).map(c -> sourceText(obs.get(c))).toList());
            carriers.stream().filter(c -> !obs.containsKey(c))
                    .forEach(c -> bad(get(m, "meaning_id") + ": onbekende drager " + c));
            if (get(m, "admission_basis").isBlank()) {
                warn(get(m, "meaning_id") + ": admission_basis leeg");
            }
        }
        for (Map<String, String> l : links.values()) {
            for (String k : List.of("from_meaning_id", "to_meaning_id")) {
                if (!means.containsKey(get(l, k))) {
                    bad(get(l, "link_id") + ": " + k + " resolvet niet");
                }
            }
            if (obs.containsKey(get(l, "observation_id"))) {
                excerptCheck(get(l, "link_id"), get(l, "source_excerpt"), List.of(sourceText(obs.get(get(l, "observation_id")))));
            }
        }
        List<String> fc = means.entrySet().stream().filter(e -> "functional_contribution".equals(e.getValue().get("admission")))
                .map(e -> e.getKey()).toList();
        Map<String, Map<String, String>> dirs = new LinkedHashMap<>();
        if (req.test(1)) {
            tsv(files, "directions.tsv", true).forEach(d -> dirs.put(get(d, "direction_id"), d));
        }
        List<String> admitted = new ArrayList<>();
        if (req.test(1)) {
            Map<String, Map<String, String>> gcell = new HashMap<>();
            tsv(files, "grouping.tsv", true).forEach(g -> gcell.put(get(g, "direction_id") + "|" + get(g, "meaning_id"), g));
            Map<String, List<String>> owner = new HashMap<>();
            for (String d : dirs.keySet()) {
                Set<String> members = new HashSet<>();
                for (String m : fc) {
                    Map<String, String> g = gcell.get(d + "|" + m);
                    if (g == null) {
                        continue;  // doc 18: a non-candidate gets no row and counts as separate
                    }
                    if ("member".equals(get(g, "decision"))) {
                        if (List.of("", Prompt1Tsv.EMPTY).contains(get(g, "basis").strip())) {
                            warn("3.4: " + m + " × " + d + " member zonder basis");
                        }
                        owner.computeIfAbsent(m, k -> new ArrayList<>()).add(d);
                        members.add(m);
                    }
                }
                if (!new HashSet<>(ids(dirs.get(d).get("member_ids"))).equals(members)) {
                    bad(d + ": member_ids wijkt af van grouping.tsv");
                }
                ids(dirs.get(d).get("context_ids")).stream().filter(x -> fc.contains(x))
                        .forEach(x -> bad(d + ": functionele bijdrage " + x + " als context"));
            }
            for (String m : fc) {
                int n = owner.getOrDefault(m, List.of()).size();
                if (n != 1) {
                    bad("3.5: " + m + " heeft " + n + " constitutieve groepen");
                }
            }
            for (Map.Entry<String, Map<String, String>> e : dirs.entrySet()) {
                for (String part : get(e.getValue(), "overlap_resolutions").split(";;")) {
                    String p = part.strip();
                    if (List.of("", Prompt1Tsv.EMPTY, "[]").contains(p)) {
                        continue;
                    }
                    Matcher m = OVERLAP.matcher(p);
                    if (!m.lookingAt()) {
                        warn("3.5: " + e.getKey() + " overlap_resolutions niet in doc-18-vorm: " + p.substring(0, Math.min(60, p.length())));
                        continue;
                    }
                    String mm = m.group(1);
                    String to = m.group(2);
                    if (!dirs.containsKey(to) || !ids(dirs.get(to).get("member_ids")).contains(mm)) {
                        bad("3.5: overlap " + mm + " niet opgelost naar een richting waarin hij lid is");
                    }
                    ids(m.group(3)).stream().filter(x -> !dirs.containsKey(x))
                            .forEach(x -> bad("3.5: overlap " + mm + " noemt onbekende " + x));
                }
            }
            // ---- 3.7A CTS admission
            Map<String, Map<String, String>> adm = new LinkedHashMap<>();
            tsv(files, "cts_admission.tsv", true).forEach(a -> adm.put(get(a, "direction_id"), a));
            for (Map.Entry<String, Map<String, String>> e : dirs.entrySet()) {
                String d = e.getKey();
                Map<String, String> a = adm.get(d);
                if (a == null) {
                    bad("3.7A: " + d + " zonder opnamebeslissing");
                    continue;
                }
                String decision = get(a, "decision");
                if (!List.of("admitted", "context").contains(decision)) {
                    bad("3.7A: " + d + " ongeldige decision " + decision);
                    continue;
                }
                if (decision.equals("admitted")) {
                    if (ids(a.get("function_basis")).isEmpty()) {
                        bad("3.7A: " + d + " admitted zonder function_basis");
                    }
                    ids(a.get("function_basis")).stream().filter(x -> !ids(e.getValue().get("member_ids")).contains(x))
                            .forEach(x -> bad("3.7A: " + d + " function_basis " + x + " is geen constitutieve betekenis van de richting"));
                    if (!List.of("belemmering", "verstoring", "overinzet").contains(get(a, "tension_type"))) {
                        bad("3.7A: " + d + " admitted met ongeldig tension_type " + get(a, "tension_type"));
                    }
                    if (ids(a.get("tension_basis")).isEmpty()) {
                        bad("3.7A: " + d + " admitted zonder tension_basis");
                    }
                    ids(a.get("tension_basis")).stream().filter(x -> !means.containsKey(x) && !links.containsKey(x))
                            .forEach(x -> bad("3.7A: " + d + " tension_basis verwijst naar onbekende " + x));
                } else if (!List.of("werking", "spanning", "relatie").contains(get(a, "missing"))) {
                    bad("3.7A: " + d + " context zonder ontbrekend element (werking/spanning/relatie)");
                }
            }
            adm.keySet().stream().filter(d -> !dirs.containsKey(d))
                    .forEach(d -> bad("3.7A: cts_admission.tsv noemt onbekende " + d));
            dirs.keySet().stream().filter(d -> "admitted".equals(get(adm.get(d), "decision"))).forEach(d -> admitted.add(d));
            if (!dirs.isEmpty() && admitted.isEmpty()) {
                bad("3.7A: geen enkele richting opgenomen (CTS_ADMISSION / no_admitted_direction)");
            }
        }

        // ---- step 2-3
        Set<String> domDirs = new HashSet<>();
        Map<String, String> ftfOf = new LinkedHashMap<>();
        if (req.test(2)) {
            Map<String, Map<String, String>> prio = new LinkedHashMap<>();
            tsv(files, "priority.tsv", true).forEach(p -> prio.put(get(p, "direction_id"), p));
            prio.keySet().stream().filter(d -> dirs.containsKey(d) && !admitted.contains(d))
                    .forEach(d -> bad("stap 2: contextrichting " + d + " heeft een prioriteit"));
            for (Map<String, String> p : prio.values()) {
                ids(p.get("context_meaning_ids")).stream().filter(x -> !means.containsKey(x))
                        .forEach(x -> bad("stap 2: " + get(p, "direction_id") + " context verwijst naar onbekende " + x));
            }
            for (String d : admitted) {
                Map<String, String> p = prio.get(d);
                if (p == null) {
                    bad("stap 2: " + d + " zonder prioriteit");
                    continue;
                }
                if (!new HashSet<>(ids(dirs.get(d).get("member_ids"))).containsAll(ids(p.get("basis_meaning_ids")))) {
                    bad("stap 2: " + d + " basis buiten eigen richting");
                }
                if (get(p, "basis").isBlank()) {
                    warn("stap 2: " + d + " zonder basis");
                }
                if (get(p, "canon_locators").isBlank()) {
                    warn("stap 2: " + d + " zonder canon_locators");
                }
            }
            prio.forEach((d, p) -> {
                if ("dominant".equals(get(p, "priority_status"))) {
                    domDirs.add(d);
                }
            });
            if (domDirs.isEmpty()) {
                bad("stap 2: geen dominante richting");
            }
            List<String> admP = admitted.stream().filter(d -> prio.containsKey(d)).toList();
            if (admP.size() >= 2 && admP.stream().allMatch(d -> "dominant".equals(get(prio.get(d), "priority_status")))) {
                bad("stap 2: alle " + admP.size() + " opgenomen richtingen dominant; vergelijk opnieuw op de gronden G1–G4");
            }
            new TreeSet<>(domDirs).stream().filter(d -> !GROUND.matcher(get(prio.get(d), "basis")).find())
                    .forEach(d -> warn("stap 2: dominante " + d + " noemt geen grond G1–G4 in basis"));
        }
        if (req.test(3)) {
            admitted.forEach(d -> ftfOf.put(d, String.format("FTF_%03d", Prompt1Tsv.number(d))));
        }
        if (req.test(3) && out != null) {
            List<String> exported = new ArrayList<>();
            out.path("ftfs").forEach(f -> exported.add(f.path("source_direction_id").asText()));
            Collections.sort(exported);
            List<String> want = new ArrayList<>(admitted);
            Collections.sort(want);
            if (!exported.equals(want)) {
                bad("stap 3: FTF's in de export ≠ opgenomen richtingen (3.7A)");
            }
            for (JsonNode f : out.path("ftfs")) {
                String ftfId = f.path("ftf_id").asText();
                Map<String, String> d = dirs.get(f.path("source_direction_id").asText());
                if (d == null) {
                    bad(ftfId + ": onbekende richting");
                    continue;
                }
                if (!Objects.equals(Prompt1Tsv.number(ftfId), Prompt1Tsv.number(f.path("source_direction_id").asText()))) {
                    bad(ftfId + ": nummer ≠ nummer van " + f.path("source_direction_id").asText());
                }
                if (!f.path("functional_directionality").asText().equals(get(d, "functional_directionality"))) {
                    bad(ftfId + ": directionality niet exact gekopieerd");
                }
                if (!f.path("identity").asText().equals(get(d, "identity"))) {
                    bad(ftfId + ": identity niet exact gekopieerd");
                }
                if ("dominant".equals(f.path("priority_status").asText()) != domDirs.contains(f.path("source_direction_id").asText())) {
                    bad(ftfId + ": dominantie wijkt af");
                }
            }
        }
        List<String> ftfs = new ArrayList<>(ftfOf.values());
        Set<String> domFtf = new HashSet<>();
        domDirs.stream().filter(d -> ftfOf.containsKey(d)).forEach(d -> domFtf.add(ftfOf.get(d)));

        // ---- step 4-5
        Map<String, List<String>> cands = new LinkedHashMap<>();
        List<String> elig = new ArrayList<>();
        Map<String, List<String>> partial = new LinkedHashMap<>();
        if (req.test(4)) {
            Map<String, Map<String, List<String>>> reqel = new LinkedHashMap<>();
            Map<String, String> ealt = new HashMap<>();
            for (String ie : ies) {
                String block = key.apply("R5:ELEM:" + ie);
                if (block == null) {
                    continue;
                }
                Matcher m = ELEM_ROW.matcher(block);
                while (m.find()) {
                    String rid = m.group(1);
                    if (!rid.startsWith(ie + "-")) {
                        continue;
                    }
                    if (m.group(5).equals("required")) {
                        reqel.computeIfAbsent(ie, k -> new LinkedHashMap<>()).computeIfAbsent(m.group(2), k -> new ArrayList<>()).add(rid);
                    }
                    if (m.group(3).equals("E")) {
                        ealt.put(rid, m.group(2));
                    }
                }
            }
            boolean r5Ok = !reqel.isEmpty();
            if (!r5Ok) {
                warn("R5-document niet gevonden in ROOT/documents: lidmaatschap alleen tegen ie_membership.tsv (server) gecontroleerd");
            }
            Map<String, Map<String, String>> core = new HashMap<>();
            for (Map<String, String> r : tsv(files, "ie_core_filter.tsv", true)) {
                String k = get(r, "ie_ref") + "|" + get(r, "ftf_id");
                if (core.containsKey(k)) {
                    bad("kernfilter: dubbele regel " + get(r, "ie_ref") + " × " + get(r, "ftf_id"));
                }
                core.put(k, r);
            }
            List<Map<String, String>> cellRows = tsv(files, "ie_ftf_cells.tsv", true);
            Map<String, Map<String, String>> cells = new HashMap<>();
            cellRows.forEach(c -> cells.put(get(c, "ie_ref") + "|" + get(c, "ftf_id") + "|" + get(c, "element_id"), c));
            Map<String, List<Integer>> lastSeq = new HashMap<>();
            for (Map<String, String> c : cellRows) {
                if (get(c, "seq").strip().matches("-?\\d+")) {
                    lastSeq.computeIfAbsent(get(c, "ie_ref"), k -> new ArrayList<>()).add(Integer.parseInt(get(c, "seq").strip()));
                } else {
                    warn("6.1C: cel " + get(c, "ie_ref") + " × " + get(c, "ftf_id") + " zonder geldige seq");
                }
                Map<String, String> k = core.get(get(c, "ie_ref") + "|" + get(c, "ftf_id"));
                if (k != null && !"yes".equals(get(k, "passes"))) {
                    bad("6.1C: cel voor " + get(c, "ie_ref") + " × " + get(c, "ftf_id") + " terwijl kernfilter = no");
                }
            }
            for (int i = 0; i + 1 < ies.size(); i++) {
                String a = ies.get(i);
                String b = ies.get(i + 1);
                if (lastSeq.containsKey(a) && lastSeq.containsKey(b)
                        && Collections.max(lastSeq.get(a)) > Collections.min(lastSeq.get(b))) {
                    warn("stap 4: cellen van " + b + " vóór afsluiting van " + a + " (niet één IE tegelijk)");
                }
            }
            for (String[] q : queries) {
                String k = Objects.requireNonNullElse(keyOf(q[1]), "");
                if (q[0].matches("\\d+") && ((k.startsWith("R5:A2:") && Integer.parseInt(q[0]) < 6)
                        || (k.startsWith("R4:INDEX:") && Integer.parseInt(q[0]) < 7))) {
                    warn("stap " + q[0] + ": " + k + " opgehaald vóór de stap waar die hoort");
                }
            }
            Map<String, String> derived = new LinkedHashMap<>();
            for (String ie : ies) {
                if (r5Ok && !reqel.containsKey(ie)) {
                    bad("stap 4: IE " + ie + " zonder vereiste elementen in R5");
                    continue;
                }
                if (!keyRead.test("R4:" + ie + ":01")) {
                    warn("stap 4: KEY R4:" + ie + ":01 niet opgehaald");
                }
                if (!keyRead.test("R5:ELEM:" + ie)) {
                    warn("stap 4: KEY R5:ELEM:" + ie + " niet opgehaald");
                }
                for (String f : ftfs) {
                    String pair = ie + "|" + f;
                    Map<String, String> k = core.get(pair);
                    if (k == null) {
                        bad("kernfilter: regel ontbreekt " + ie + " × " + f);
                        derived.put(pair, null);
                        continue;
                    }
                    if (!List.of("yes", "no").contains(get(k, "passes"))) {
                        bad("kernfilter: ongeldige passes " + ie + " × " + f);
                    }
                    if (!"yes".equals(get(k, "passes"))) {
                        derived.put(pair, null);
                        continue;
                    }
                    List<String> supported = ids(k.get("element_ids"));
                    if (supported.isEmpty()) {
                        bad("kernfilter: " + ie + " × " + f + " passes = yes zonder element_ids");
                    }
                    if (ids(k.get("meaning_ids")).isEmpty()) {
                        bad("kernfilter: " + ie + " × " + f + " passes = yes zonder meaning_ids");
                    }
                    if (!r5Ok) {
                        derived.put(pair, "?");
                        continue;
                    }
                    List<String> alts = new ArrayList<>();
                    for (String el : supported) {
                        if (!ealt.containsKey(el)) {
                            bad("kernfilter: " + ie + " × " + f + " onbekend element " + el);
                        } else if (!alts.contains(ealt.get(el))) {
                            alts.add(ealt.get(el));
                        }
                    }
                    String memberAlt = null;
                    for (String alt : reqel.get(ie).keySet().stream().filter(a -> alts.contains(a)).toList()) {
                        boolean allS = true;
                        for (String el : reqel.get(ie).get(alt)) {
                            Map<String, String> c = cells.get(ie + "|" + f + "|" + el);
                            String where = ie + " × " + f + " × " + el;
                            if (c == null) {
                                bad("6.1C: cel ontbreekt " + where);
                                allS = false;
                                continue;
                            }
                            String st = get(c, "status");
                            if (!List.of("S", "NE", "C").contains(st)) {
                                bad("6.1C: ongeldige status " + where);
                            }
                            if (List.of("S", "C").contains(st) && ids(c.get("meaning_ids")).isEmpty()) {
                                bad("6.1C: " + where + " " + st + " zonder cliënttegenhanger");
                            }
                            if (st.equals("S") && List.of("", Prompt1Tsv.EMPTY).contains(get(c, "basis").strip())) {
                                warn("6.1C: " + where + " S zonder basis");
                            }
                            if (st.equals("NE") && List.of("", Prompt1Tsv.EMPTY).contains(get(c, "missing").strip())) {
                                warn("4.3: " + where + " NE zonder ontbrekend element");
                            } else if (st.equals("NE") && ELEMENT_IDS_ONLY.matcher(get(c, "missing").strip()).matches()) {
                                warn("6.1 (v0.131): " + where + " NE noemt alleen het element-ID; noem de ontbrekende cliëntinhoud in een korte woordgroep");
                            }
                            if (st.equals("C") && ids(c.get("counterclaims")).isEmpty()) {
                                warn("6.1: " + where + " C zonder tegenclaim");
                            }
                            List<String> refs = new ArrayList<>(ids(c.get("meaning_ids")));
                            List<String> claims = new ArrayList<>(ids(c.get("counterclaims")));
                            claims.addAll(ids(c.get("supporting_claims")));
                            claims.stream().filter(x -> MEANING.matcher(x).matches()).forEach(x -> refs.add(x));
                            refs.stream().filter(x -> !means.containsKey(x))
                                    .forEach(x -> bad("6.1C: " + where + " verwijst naar onbekende " + x));
                            ids(c.get("link_ids")).stream().filter(x -> !links.containsKey(x))
                                    .forEach(x -> bad("6.1C: " + where + " verwijst naar onbekende " + x));
                            if (!st.equals("S")) {
                                allS = false;
                            }
                        }
                        if (allS && memberAlt == null) {
                            memberAlt = "A" + alt;
                        }
                    }
                    derived.put(pair, memberAlt);
                }
            }
            Set<String> mrows = new LinkedHashSet<>();
            tsv(files, "ie_membership.tsv", false).forEach(r -> mrows.add(get(r, "ie_ref") + "|" + get(r, "ftf_id")));
            if (!files.containsKey("ie_membership.tsv")) {
                bad("stap 4: derive_ie_candidates niet aangeroepen (ie_membership.tsv ontbreekt)");
            }
            for (Map.Entry<String, String> e : new ArrayList<>(derived.entrySet())) {
                if ("?".equals(e.getValue())) {
                    derived.put(e.getKey(), mrows.contains(e.getKey()) ? "A?" : null);
                    continue;
                }
                if ((e.getValue() != null) != mrows.contains(e.getKey())) {
                    String[] p = e.getKey().split("\\|");
                    bad("6.1D: lidmaatschap " + p[0] + " × " + p[1] + " in ie_membership.tsv volgt niet uit de cellen");
                }
            }
            for (String k : mrows) {
                if (!derived.containsKey(k)) {
                    String[] p = k.split("\\|");
                    bad("6.1D: ie_membership.tsv noemt onbekend paar " + p[0] + " × " + p[1]);
                }
            }
            for (String ie : ies) {
                List<String> v = ftfs.stream().filter(f -> derived.get(ie + "|" + f) != null).toList();
                if (!v.isEmpty()) {
                    cands.put(ie, v);
                }
            }
            cands.forEach((ie, v) -> {
                if (v.stream().anyMatch(f -> domFtf.contains(f))) {
                    elig.add(ie);
                }
            });
            for (String ie : ies) {
                List<String> v = new ArrayList<>(domFtf.stream()
                        .filter(f -> "yes".equals(get(core.get(ie + "|" + f), "passes"))).toList());
                Collections.sort(v);
                if (!v.isEmpty()) {
                    partial.put(ie, v);
                }
            }
            new TreeSet<>(domFtf).stream().filter(f -> partial.values().stream().noneMatch(v -> v.contains(f)))
                    .forEach(f -> bad("stap 4 (2a): dominante " + f + " komt bij geen enkele IE door de kernfilter; herbeoordeel met de twijfelregel"));
        }
        if (req.test(5)) {
            List<Map<String, String>> sel = tsv(files, "a1_selection.tsv", true);
            if (!sel.isEmpty()) {
                List<String> primRows = sel.stream().filter(r -> get(r, "step").startsWith("7.2D")
                        && "primary".equals(get(r, "finding").strip())).map(r -> get(r, "ie_ref")).toList();
                if (primRows.size() != 1) {
                    bad("7.2D: a1_selection.tsv heeft niet precies één primaire IE (v0.128: altijd een primaire IE)");
                } else if (!elig.isEmpty() && !elig.contains(primRows.get(0))) {
                    bad("7.2: primaire IE " + primRows.get(0) + " is niet primair-geschikt");
                }
                if (primRows.size() == 1 && req.test(4)) {
                    Set<String> judged = new HashSet<>();
                    sel.stream().filter(r -> get(r, "step").startsWith("7.3")
                                    && List.of("secondary", "redundant").contains(get(r, "finding").strip()))
                            .forEach(r -> judged.add(get(r, "ie_ref")));
                    cands.keySet().stream().filter(ie -> !ie.equals(primRows.get(0)) && !judged.contains(ie))
                            .forEach(ie -> bad("7.3: kandidaat " + ie + " zonder regel 7.3 (secondary of redundant) in a1_selection.tsv"));
                }
                if (primRows.size() == 1 && elig.isEmpty() && req.test(4)) {
                    if (!partial.containsKey(primRows.get(0))) {
                        bad("7.1: partial-fit primaire IE " + primRows.get(0) + " heeft geen dominante FTF die door de kernfilter komt");
                    }
                    if (sel.stream().noneMatch(r -> get(r, "step").startsWith("7.1") && "partial_fit".equals(get(r, "finding").strip()))) {
                        bad("7.1: geen primair-geschikte IE, maar de regel 7.1 | — | partial_fit ontbreekt");
                    }
                }
            }
        }
        if (req.test(5) && out != null) {
            JsonNode a1 = out.path("a1");
            JsonNode primaryIe = a1.path("primary_ie");
            boolean hasPrimary = primaryIe.isObject() && !primaryIe.isEmpty();
            List<JsonNode> selected = new ArrayList<>();
            if (hasPrimary) {
                selected.add(primaryIe);
            }
            a1.path("secondary_ie").forEach(x -> selected.add(x));
            String pfit = hasPrimary && elig.isEmpty() ? primaryIe.path("ie_ref").asText() : null;
            Set<String> secs = new TreeSet<>();
            a1.path("secondary_ie").forEach(x -> secs.add(x.path("ie_ref").asText()));
            List<Map<String, String>> selRows = tsv(files, "a1_selection.tsv", true);
            Set<String> secRows = new TreeSet<>();
            selRows.stream().filter(r -> get(r, "step").startsWith("7.3") && "secondary".equals(get(r, "finding").strip()))
                    .forEach(r -> secRows.add(get(r, "ie_ref")));
            if (!selRows.isEmpty() && !secs.equals(secRows)) {
                bad("A1: secundaire IE's in de export " + secs + " ≠ a1_selection.tsv " + secRows);
            }
            if (!cands.keySet().containsAll(secs)) {
                bad("A1: secundaire IE's " + secs + " zijn geen kandidaten " + new TreeSet<>(cands.keySet()));
            }
            List<String> prow = selRows.stream().filter(r -> get(r, "step").startsWith("7.2D")
                    && "primary".equals(get(r, "finding").strip())).map(r -> get(r, "ie_ref")).toList();
            if (hasPrimary && !prow.isEmpty() && !primaryIe.path("ie_ref").asText().equals(prow.get(0))) {
                bad("A1: primaire IE in de export (" + primaryIe.path("ie_ref").asText() + ") ≠ a1_selection.tsv (" + prow.get(0)
                        + "); kopieer A1 uit a1_selection.tsv");
            }
            for (JsonNode x : selected) {
                String ie = x.path("ie_ref").asText();
                List<String> represented = new ArrayList<>();
                x.path("represented_ftf_ids").forEach(f -> represented.add(f.asText()));
                if (cands.containsKey(ie) && !represented.equals(cands.get(ie))) {
                    bad("A1: " + ie + " represented_ftf_ids wijkt af van de cellen");
                }
            }
            if (!hasPrimary) {
                bad("7.1: A1 zonder primaire IE (v0.128: altijd een primaire IE)");
            } else if (!elig.isEmpty() && !elig.contains(primaryIe.path("ie_ref").asText())) {
                bad("7.2: primaire IE is niet primair-geschikt");
            } else if (pfit != null && !partial.containsKey(pfit)) {
                bad("7.1: partial-fit primaire IE " + pfit + " heeft geen dominante FTF die door de kernfilter komt");
            }
            if (elig.size() > 1 && selRows.isEmpty()) {
                bad("7.2: meerdere geschikte IE's zonder vastgelegde selectie");
            }
            List<String> order = new ArrayList<>();
            a1.path("secondary_ie").forEach(x -> order.add(x.path("ie_ref").asText()));
            List<String> canonical = new ArrayList<>(order);
            canonical.sort(Comparator.comparingInt(ie -> ies.indexOf(ie)));
            if (!order.equals(canonical)) {
                warn("7.3: secundaire IE's niet in canonieke volgorde");
            }
        }

        // ---- step 6 (A2)
        String prim = out != null && out.path("a1").path("primary_ie").isObject()
                && !out.path("a1").path("primary_ie").isEmpty() ? out.path("a1").path("primary_ie").path("ie_ref").asText() : null;
        if (req.test(6) && out != null && out.has("a2")) {  // v07: A2 is no longer produced (SM v0.138); only old runs have it
            Map<String, String> roles = new LinkedHashMap<>();
            if (prim != null) {
                roles.put(prim, "primary");
            }
            out.path("a1").path("secondary_ie").forEach(x -> roles.put(x.path("ie_ref").asText(), "secondary"));
            for (Map.Entry<String, String> e : roles.entrySet()) {
                String ie = e.getKey();
                List<JsonNode> rows = new ArrayList<>();
                out.path("a2").path("fields").forEach(x -> {
                    if (ie.equals(x.path("source_ie_ref").asText())) {
                        rows.add(x);
                    }
                });
                if (rows.isEmpty()) {
                    bad("A2: " + ie + " zonder veld");
                }
                for (JsonNode r : rows) {
                    String field = r.path("field_id").asText();
                    if (!r.path("field_role").asText().equals(e.getValue())) {
                        bad("A2: " + field + " field_role ≠ rol van " + ie);
                    }
                    String domain = r.path("domain_id").asText("");
                    if (!domain.isEmpty() && !domain.equals("DOMAIN_" + field)) {
                        warn("A2: " + field + " domain_id niet DOMAIN_<FIELD_ID>");
                    }
                }
                String block = key.apply("R5:A2:" + ie);
                if (block != null) {
                    LinkedHashSet<String> expect = new LinkedHashSet<>();
                    Matcher m = Pattern.compile("FIELD_" + Pattern.quote(ie) + "_\\d+(?=\\s*\\|)").matcher(block);
                    while (m.find()) {
                        expect.add(m.group());
                    }
                    if (!rows.stream().map(r -> r.path("field_id").asText()).toList().equals(new ArrayList<>(expect))) {
                        bad("A2: velden van " + ie + " ≠ R5:A2 (" + expect + ")");
                    }
                }
            }
        }

        // ---- step 7-10
        List<String> a3c = new ArrayList<>();
        if (req.test(7) && out != null && prim != null) {
            String block = key.apply("R4:INDEX:" + prim);
            if (block == null) {
                warn("R4-document niet gevonden: A3-kandidatenset uit de export overgenomen");
            } else {
                Matcher mm = Pattern.compile("members:\\s*\\n((?:\\s*22\\.\\d+\\s*\\n)+)").matcher(block + "\n");
                if (mm.find()) {
                    Matcher refs = Pattern.compile("22\\.\\d+").matcher(mm.group(1));
                    while (refs.find()) {
                        a3c.add(refs.group());
                    }
                }
                if (a3c.isEmpty()) {
                    bad("stap 7: geen HETA-leden gevonden in R4:INDEX:" + prim);
                }
            }
            if (a3c.isEmpty()) {
                out.path("a3").forEach(a -> a3c.add(a.path("heta_ref").asText()));
            }
        }
        if (req.test(7) && prim != null && !keyRead.test("R4:INDEX:" + prim)) {
            warn("stap 7: R4:INDEX van primaire IE niet opgehaald");
        }
        for (String[] q : queries) {
            if (q[1].contains("R4:INDEX:") && q[0].matches("\\d+") && Integer.parseInt(q[0]) < 7) {
                warn("stap 7: R4:INDEX vóór stap 7 opgehaald");
            }
        }
        Map<String, String> clsDerived = new LinkedHashMap<>();
        Map<String, String> routeDerived = new LinkedHashMap<>();
        if (req.test(9)) {
            Set<String> errs = new HashSet<>();
            rlog.stream().filter(r -> get(r, "chunk_ids").startsWith("ERROR")).forEach(r -> errs.add(keyOf(get(r, "query"))));
            for (String h : a3c) {
                for (String section : HETA_SECTIONS) {
                    String k = "R3:" + h + section.replace("{prim}", Objects.requireNonNullElse(prim, ""));
                    if (section.equals(".4:SIG") && key.apply(k) == null) {
                        continue;  // 22.61, 22.64: no signature
                    }
                    if (!keyRead.test(k)) {
                        warn("stap 9: KEY " + k + " niet in retrieval_log");
                    } else if (errs.contains(k) && rlog.stream().noneMatch(r -> k.equals(keyOf(get(r, "query")))
                            && !get(r, "chunk_ids").startsWith("ERROR"))) {
                        warn("stap 9: KEY " + k + " alleen met foutmelding opgehaald");
                    }
                }
            }
        }
        if (req.test(10)) {
            List<Map<String, String>> canon = tsv(files, "heta_canon.tsv", true);
            List<Map<String, String>> hcells = tsv(files, "heta_cells.tsv", true);
            for (String h : a3c) {
                List<Map<String, String>> hc = canon.stream().filter(c -> h.equals(c.get("heta_ref"))).toList();
                if (hc.stream().noneMatch(c -> "contribution".equals(c.get("kind")))) {
                    bad("12.1: " + h + " zonder eigen bijdrage");
                }
                for (Map<String, String> c : hc) {
                    if ("signal".equals(c.get("kind")) && !"yes".equals(c.get("applicable"))) {
                        continue;
                    }
                    String route = "contribution".equals(c.get("kind")) ? "functional_correspondence" : "canonical_signal";
                    String cid = get(c, "canonical_id");
                    List<Map<String, String>> rows = hcells.stream().filter(x -> h.equals(x.get("heta_ref"))
                            && cid.equals(x.get("canonical_id")) && route.equals(x.get("route"))).toList();
                    if (rows.size() != 1) {
                        bad("12.2: " + h + " × " + cid + " × " + route + ": " + rows.size() + " regels");
                    }
                    for (Map<String, String> x : rows) {
                        String state = get(x, "state");
                        if (!List.of("S", "PART", "NE", "C").contains(state)) {
                            bad("12.2: ongeldige staat " + h + " " + cid);
                        }
                        if (List.of("S", "PART").contains(state) && ids(x.get("meaning_ids")).isEmpty()) {
                            bad("12.2: " + h + " " + cid + " zonder cliëntdrager");
                        }
                        if (state.equals("PART") && get(x, "gap").isBlank()) {
                            bad("12.3: " + h + " " + cid + " PART zonder gat");
                        }
                        if (!state.equals("NE") && List.of("", Prompt1Tsv.EMPTY).contains(get(x, "basis").strip())) {
                            warn("12.2: " + h + " " + cid + " zonder basis");
                        }
                    }
                }
                List<Map<String, String>> hr = hcells.stream().filter(x -> h.equals(x.get("heta_ref"))).toList();
                Set<String> app = new HashSet<>();
                hc.stream().filter(c -> "yes".equals(c.get("applicable"))).forEach(c -> app.add(get(c, "canonical_id")));
                boolean contribS = hr.stream().anyMatch(x -> "S".equals(x.get("state")) && "functional_correspondence".equals(x.get("route")));
                boolean contribPart = hr.stream().anyMatch(x -> "PART".equals(x.get("state")) && "functional_correspondence".equals(x.get("route")));
                boolean signalS = hr.stream().anyMatch(x -> "S".equals(x.get("state")) && "canonical_signal".equals(x.get("route"))
                        && app.contains(get(x, "canonical_id")));
                clsDerived.put(h, contribS ? "direct" : (contribPart || signalS) ? "indirect" : "not_eligible");
                routeDerived.put(h, contribS || contribPart ? "functional_correspondence" : signalS ? "canonical_signal" : "none");
            }
            if (out != null) {
                Map<String, JsonNode> got = new LinkedHashMap<>();
                out.path("a3").forEach(a -> got.put(a.path("heta_ref").asText(), a));
                if (!new ArrayList<>(got.keySet()).equals(a3c)) {
                    bad("stap 10: A3-items ≠ kandidatenset of andere volgorde");
                }
                clsDerived.forEach((h, c) -> {
                    JsonNode a = got.get(h);
                    if (a == null || !c.equals(a.path("classification").asText())) {
                        bad("12.4: " + h + " classificatie volgt niet uit de cellen (" + c + ")");
                    }
                    if (a == null || !routeDerived.get(h).equals(a.path("decision_route").asText())) {
                        bad("12.4: " + h + " decision_route ≠ " + routeDerived.get(h));
                    }
                });
            }
        }

        // ---- split execution files, only when present
        if (files.containsKey("a3_candidates.tsv") && !a3c.isEmpty()) {
            List<String> cand = Prompt1Tsv.candidateOrder(files.get("a3_candidates.tsv"));
            if (!cand.equals(a3c)) {
                bad("split: a3_candidates.tsv ≠ R4:INDEX:" + prim + " (" + a3c + ")");
            }
        }
        if (files.containsKey("a3_decisions.tsv")) {
            List<Map<String, String>> dec = Prompt1Tsv.rows(files.get("a3_decisions.tsv"));
            List<String> refs = dec.stream().map(d -> get(d, "heta_ref")).toList();
            List<String> sortedRefs = new ArrayList<>(refs);
            Collections.sort(sortedRefs);
            List<String> sortedA3c = new ArrayList<>(a3c);
            Collections.sort(sortedA3c);
            if (!a3c.isEmpty() && (!sortedRefs.equals(sortedA3c) || new HashSet<>(refs).size() != refs.size())) {
                bad("split: a3_decisions.tsv heeft niet precies één regel per kandidaat");
            }
            Map<String, String> exported = new HashMap<>();
            if (out != null) {
                out.path("a3").forEach(a -> exported.put(a.path("heta_ref").asText(), a.path("classification").asText()));
            }
            for (Map<String, String> d : dec) {
                String h = get(d, "heta_ref");
                if (clsDerived.containsKey(h) && !get(d, "classification").equals(clsDerived.get(h))) {
                    bad("split: " + h + " classificatie in a3_decisions ≠ cellen (" + clsDerived.get(h) + ")");
                }
                if (out != null && !get(d, "classification").equals(exported.get(h))) {
                    bad("split: " + h + " classificatie in a3_decisions ≠ export");
                }
            }
        }
        if (files.containsKey("a3_evidence_basis.tsv")) {
            for (Map<String, String> r : Prompt1Tsv.rows(files.get("a3_evidence_basis.tsv"))) {
                String m = get(r, "meaning_id");
                String role = get(r, "role");
                if (!means.containsKey(m)) {
                    bad("split: a3_evidence_basis " + m + " niet in meanings.tsv");
                }
                if (!List.of("constitutive", "context", "linked_context", "primary_ie_context").contains(role)) {
                    bad("split: ongeldige rol " + role);
                }
                if (role.equals("linked_context") && List.of("", Prompt1Tsv.EMPTY).contains(get(r, "link_id").strip())) {
                    bad("split: linked_context " + m + " zonder link_id");
                }
                if (role.equals("context")) {
                    Map<String, String> d = dirs.get(get(r, "direction_id").split(",")[0].strip());
                    if (d != null && !ids(d.get("context_ids")).contains(m)) {
                        bad("split: context " + m + " staat niet in context_ids van " + get(r, "direction_id"));
                    } else if (d != null && List.of("", Prompt1Tsv.EMPTY).contains(get(r, "link_id").strip())) {
                        Set<String> memberObs = new HashSet<>();
                        ids(d.get("member_ids")).stream().filter(x -> means.containsKey(x))
                                .forEach(x -> memberObs.add(get(means.get(x), "observation_id")));
                        if (!memberObs.contains(get(means.get(m), "observation_id"))) {
                            bad("split: context " + m + " zonder link_id en niet in dezelfde observatie als een constitutieve betekenis");
                        }
                    }
                }
            }
        }

        // ---- step 12-14
        Map<String, JsonNode> qa = new LinkedHashMap<>();
        String dob = null;
        if (out != null) {
            for (IntakeFile file : intake) {
                try {
                    JsonNode doc = MAPPER.readTree(new String(file.content(), StandardCharsets.UTF_8).replace("﻿", ""));
                    JsonNode data = doc.path("structured_data");
                    if (data.has("bio")) {
                        dob = data.path("bio").path("date_of_birth").asText(null);
                    }
                    for (JsonNode section : data.path("sections")) {
                        section.path("questions").forEach(q -> qa.put(q.path("id").asText(), q));
                        for (JsonNode sub : section.path("subsections")) {
                            sub.path("questions").forEach(q -> qa.put(q.path("id").asText(), q));
                        }
                    }
                } catch (Exception e) {
                    warn("intake-bestand " + file.name() + " niet leesbaar als JSON");
                }
            }
        }
        if (req.test(12) && out != null) {
            java.util.function.Predicate<JsonNode> isJa = q -> q != null
                    && q.path("answer").asText("").strip().toLowerCase(Locale.ROOT).startsWith("ja");
            boolean trig = isJa.test(qa.get("1.7")) || qa.entrySet().stream().anyMatch(e -> e.getKey().split("\\.")[0].equals("2")
                    && e.getKey().chars().filter(ch -> ch == '.').count() == 2 && isJa.test(e.getValue()));
            List<String> want = new ArrayList<>();
            if (trig) {
                if (dob == null) {
                    bad("B1: geboortedatum ontbreekt in de persoonsgegevens");
                } else {
                    int age = Prompt1TurnOutput.ageOn(dob, out.path("current_run_date").asText());
                    want.add(age <= 7 ? "ethos-scan-child" : age <= 17 ? "ethos-scan-hybride" : "ethos-scan-adult");
                }
            }
            List<String> got = new ArrayList<>();
            out.path("b1").forEach(x -> got.add(x.path("qest_json").asText()));
            if (!got.equals(want)) {
                bad("B1: verwacht " + want);
            }
        }
        if (req.test(13) && out != null) {
            boolean ytrig;
            try {
                double a = Double.parseDouble(qa.get("5.8").path("answer").asText().strip().replace(',', '.'));
                double b = Double.parseDouble(qa.get("5.9").path("answer").asText().strip().replace(',', '.'));
                double c = Double.parseDouble(qa.get("5.10").path("answer").asText().strip().replace(',', '.'));
                ytrig = a + b + c >= 6 || a >= 3;
            } catch (RuntimeException e) {
                ytrig = false;
                warn("15.3: YSQ-scores 5.8–5.10 niet (volledig) numeriek in de intake; trigger = false aangenomen");
            }
            boolean hasYsq = false;
            for (JsonNode x : out.path("b2").path("items")) {
                hasYsq |= "ysq".equals(x.path("qest_json").asText());
            }
            if (ytrig != hasYsq) {
                bad("15.3/15.4: YSQ in B2 volgt niet uit de intake-scores (verwacht " + (ytrig ? "ja" : "nee") + ")");
            }
            List<Map<String, String>> needs = tsv(files, "b2_needs.tsv", true);
            List<Map<String, String>> cov = tsv(files, "b2_coverage.tsv", true);
            for (JsonNode a : out.path("a3")) {
                String h = a.path("heta_ref").asText();
                if ("indirect".equals(a.path("classification").asText()) && needs.stream().noneMatch(n -> h.equals(n.get("heta_ref")))) {
                    bad("B2: indirecte " + h + " zonder observatiebehoefte");
                }
            }
            for (Map<String, String> n : needs) {
                for (String q : config.b2Order()) {
                    long count = cov.stream().filter(c -> get(c, "gap_id").equals(get(n, "gap_id")) && q.equals(c.get("qest_json"))).count();
                    if (count != 1) {
                        bad("15.2: dekking " + get(n, "gap_id") + " × " + q + " niet precies één regel");
                    }
                }
            }
            if (out.path("b2").has("needs")) {
                List<String> exp = needs.stream().map(n -> get(n, "gap_id") + "|" + get(n, "heta_ref") + "|" + get(n, "canonical_id")
                        + "|" + get(n, "unknown_content").strip()).toList();
                List<String> got = new ArrayList<>();
                out.path("b2").path("needs").forEach(x -> got.add(x.path("gap_id").asText() + "|" + x.path("heta_ref").asText() + "|"
                        + x.path("canonical_id").asText() + "|" + x.path("unknown_content").asText("").strip()));
                if (!got.equals(exp)) {
                    bad("15.4: b2.needs wijkt af van b2_needs.tsv");
                }
            }
            Map<String, List<String>> dims = new HashMap<>();
            for (Map<String, String> n : needs) {
                String value = n.containsKey("observable_dimensions") ? get(n, "observable_dimensions") : Prompt1Tsv.EMPTY;
                dims.put(get(n, "gap_id"), Arrays.stream(value.split(";;")).map(d -> d.strip())
                        .filter(d -> !d.isEmpty() && !d.equals(Prompt1Tsv.EMPTY)).toList());
            }
            for (Map<String, String> c : cov) {
                if (!"yes".equals(c.get("covers"))) {
                    continue;
                }
                List<String> dd = dims.getOrDefault(get(c, "gap_id"), List.of());
                if (dd.isEmpty()) {
                    bad("15.2: " + get(c, "gap_id") + " × " + get(c, "qest_json") + " dekt, maar de gap heeft geen observable_dimensions");
                } else if (dd.stream().noneMatch(d -> get(c, "covered_information").strip().startsWith(d))) {
                    warn("15.2: " + get(c, "gap_id") + " × " + get(c, "qest_json")
                            + ": covered_information begint niet met een observable_dimension van de gap");
                }
            }
            Set<String> covered = new HashSet<>();
            cov.stream().filter(c -> "yes".equals(c.get("covers"))).forEach(c -> covered.add(get(c, "gap_id")));
            TreeSet<String> uncovered = new TreeSet<>();
            needs.stream().map(n -> get(n, "gap_id")).filter(g -> !covered.contains(g)).forEach(g -> uncovered.add(g));
            TreeSet<String> exportedUncovered = new TreeSet<>();
            out.path("b2").path("uncovered_gap_ids").forEach(x -> exportedUncovered.add(x.asText()));
            if (!new ArrayList<>(uncovered).equals(new ArrayList<>(exportedUncovered))) {
                bad("B2: uncovered_gap_ids volgt niet uit de dekking");
            }
            List<String> selQ = config.b2Order().stream().filter(q -> cov.stream()
                    .anyMatch(c -> q.equals(c.get("qest_json")) && "yes".equals(c.get("covers")))).toList();
            List<String> items = new ArrayList<>();
            out.path("b2").path("items").forEach(x -> {
                if (!"ysq".equals(x.path("qest_json").asText())) {
                    items.add(x.path("qest_json").asText());
                }
            });
            if (!items.equals(selQ)) {
                bad("B2: items volgen niet uit de dekking (doc-07-volgorde)");
            }
        }
        if (req.test(14) && out != null) {
            List<String> direct = new ArrayList<>();
            out.path("a3").forEach(a -> {
                if ("direct".equals(a.path("classification").asText())) {
                    direct.add(a.path("heta_ref").asText());
                }
            });
            List<String> got = new ArrayList<>();
            boolean missingRef = false;
            for (JsonNode x : out.path("b3")) {
                if (x.hasNonNull("heta_ref")) {
                    got.add(x.path("heta_ref").asText());
                } else {
                    missingRef = true;
                }
            }
            if (!missingRef) {
                if (!got.equals(direct.stream().filter(h -> got.contains(h)).toList())) {
                    bad("B3: items zijn geen directe HETA's in A3-volgorde");
                }
                if (got.size() != direct.size()) {
                    warn("B3: " + (direct.size() - got.size()) + " directe HETA('s) zonder module-item; controleer doc 05");
                }
            } else if (out.path("b3").size() > direct.size()) {
                bad("B3: meer items dan directe HETA's");
            }
            for (JsonNode x : out.path("b3")) {
                if (!"High".equals(x.path("tier").asText())) {
                    bad("B3: tier is niet 'High'");
                    break;
                }
            }
        }

        // ---- status consistency (0.1)
        if (out != null) {
            int last = -1;
            for (int i = 0; i <= S; i++) {
                if ("closed".equals(out.path("stage_status").path("step_" + i).asText())
                        && "closed".equals(get(tstat.get(String.valueOf(i)), "status"))) {
                    last = i;
                } else {
                    break;
                }
            }
            if (out.path("completed_through_step").asInt(-99) != last) {
                bad("status: completed_through_step " + out.path("completed_through_step").asText() + " ≠ " + last);
            }
            String expect = S == 14 && last == 14 ? "valid" : S < 14 && last == S ? "stopped" : "invalid";
            if (!expect.equals(out.path("run_status").asText())) {
                bad("status: run_status " + out.path("run_status").asText() + " ≠ " + expect + " volgens de stappen");
            }
        }
    }
}
