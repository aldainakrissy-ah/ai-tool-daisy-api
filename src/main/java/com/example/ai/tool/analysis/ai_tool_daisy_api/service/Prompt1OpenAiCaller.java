package com.example.ai.tool.analysis.ai_tool_daisy_api.service;

import com.example.ai.tool.analysis.ai_tool_daisy_api.pojo.Prompt1ExperimentResult.CallUsage;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnRunService.Answer;
import com.example.ai.tool.analysis.ai_tool_daisy_api.service.Prompt1TurnRunService.TurnFailedException;
import com.openai.client.OpenAIClient;
import com.openai.core.RequestOptions;
import com.openai.errors.BadRequestException;
import com.openai.models.Reasoning;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * The OpenAI calls of the turn-based Prompt 1 route: one Responses API call per turn, correction or
 * continuation, in background mode with polling (Jan's v05b), with retries for transient errors and the
 * cost of each call at its own model's prices. Kept apart from the turn flow in {@link Prompt1TurnRunService}.
 */
@Slf4j
@Service
public class Prompt1OpenAiCaller {

    private final OpenAIClient client;
    private final Prompt1ExperimentService experimentService;

    @Value("${experiment.prompt1.max-output-tokens:64000}")
    private long maxOutputTokens;

    @Value("${experiment.prompt1.request-timeout-minutes:30}")
    private long requestTimeoutMinutes;

    @Value("${experiment.prompt1.cache-key:daisy-prompt1}")
    private String cacheKeyPrefix;

    /** v05b: model calls in OpenAI background mode, polled every poll-seconds, cancelled after max-minutes. */
    @Value("${experiment.prompt1.turns.background:true}")
    private boolean background;

    @Value("${experiment.prompt1.turns.poll-seconds:15}")
    private long pollSeconds;

    @Value("${experiment.prompt1.turns.max-minutes:45}")
    private long maxMinutes;

    /** USD per 1M tokens per model: model=input/cached/cache-write/output, comma-separated. */
    @Value("${experiment.prompt1.turns.prices:gpt-6.1-terra=1.00/0.05/1.25/5.00,gpt-6.1-sol=2.00/0.10/2.50/10.00,"
            + "gpt-6-luna=0.10/0.01/0.125/0.50,gpt-5.6-luna=0.20/0.02/0.25/1.20}")
    private String prices;
    /** {@link #prices} parsed on first use. */
    private volatile Map<String, double[]> parsedPrices;

    public Prompt1OpenAiCaller(OpenAIClient client, Prompt1ExperimentService experimentService) {
        this.client = client;
        this.experimentService = experimentService;
    }

    /**
     * One OpenAI call: the cut SM as instructions, no tools, plain text. A correction or continuation also
     * sends the previous response ID; the Responses API does not carry instructions over from it, so the
     * SM cut is sent again (Jan's runner sends none there; cached input keeps the cost low).
     *
     * @param model  the model of this call (resolved by the caller from the run's plan)
     * @param effort the reasoning effort of this call, or blank for the model's default
     */
    public Answer ask(String turn, String label, String instructions, String input, String previousResponseId,
                      String model, String effort) {
        ResponseCreateParams.Builder params = ResponseCreateParams.builder()
                .model(model)
                .input(input)
                .maxOutputTokens(maxOutputTokens)
                .store(true)
                .promptCacheKey(cacheKeyPrefix + "-turns-" + Prompt1TurnConfig.tableKey(turn).toLowerCase(Locale.ROOT));
        if (effort != null && !effort.isBlank()) {
            params.reasoning(Reasoning.builder().effort(Prompt1ExperimentService.parseReasoningEffort(effort)).build());
        }
        if (previousResponseId != null) {
            params.previousResponseId(previousResponseId);
        }
        if (instructions != null) {
            params.instructions(instructions);
        }
        ResponseCreateParams built = params.build();
        boolean useBackground = background;
        for (int attempt = 0; ; attempt++) {
            long start = System.currentTimeMillis();
            try {
                Response response;
                if (useBackground) {
                    try {
                        response = createInBackground(turn, label, built);
                    } catch (BadRequestException e) {
                        if (!String.valueOf(e.getMessage()).toLowerCase(Locale.ROOT).contains("background")) {
                            throw e;
                        }
                        log.warn("Prompt 1 turn {}: the model refuses background mode; ordinary call instead", label);
                        useBackground = false;
                        response = createDirect(built);
                    }
                } else {
                    response = createDirect(built);
                }
                CallUsage usage = withModelCost(model,
                        Prompt1ExperimentService.usageOf(label + " · " + model, response, System.currentTimeMillis() - start));
                log.info("Prompt 1 turn {}: {} {}ms input={} cached={} output={} cost=${}", label, usage.getStatus(),
                        usage.getDurationMs(), usage.getInputTokens(), usage.getCachedInputTokens(),
                        usage.getOutputTokens(), usage.getEstimatedCostUsd());
                if (ResponseStatus.INCOMPLETE.equals(response.status().orElse(null))) {
                    throw new TurnFailedException(turn, "answer cut off (max_output_tokens = " + maxOutputTokens + " too low?)");
                }
                return new Answer(outputText(response), response.id(), usage);
            } catch (TurnFailedException e) {
                throw e;
            } catch (BadRequestException e) {
                throw new TurnFailedException(turn, "API: " + e.getMessage());
            } catch (RuntimeException e) {
                if (attempt == 3) {
                    throw new TurnFailedException(turn, "API error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                log.warn("Prompt 1 turn {}: {} (retry {} of 3)", label, e.getMessage(), attempt + 1);
                try {
                    Thread.sleep(2000L << attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new TurnFailedException(turn, "interrupted");
                }
            }
        }
    }

    /**
     * v05b (Jan's D10): the call runs in OpenAI background mode and its answer is fetched by polling, with a
     * sign of life every minute and a time limit; a silent connection can then no longer make a run hang.
     * A status failed or cancelled counts as an API error (the caller retries).
     */
    /** The ordinary call, without background mode (one request held open up to the request timeout). */
    private Response createDirect(ResponseCreateParams params) {
        return client.responses().create(params, RequestOptions.builder()
                .timeout(Duration.ofMinutes(requestTimeoutMinutes)).build());
    }

    private Response createInBackground(String turn, String label, ResponseCreateParams params) {
        RequestOptions network = RequestOptions.builder().timeout(Duration.ofSeconds(120)).build();
        Response first = client.responses().create(params.toBuilder().background(true).build(), network);
        Response done = poll(turn, first, response -> client.responses().retrieve(response.id(), network),
                response -> response.status().map(status -> status.asString()).orElse("queued"),
                response -> client.responses().cancel(response.id()),
                pollSeconds * 1000L, maxMinutes * 60_000L, () -> System.currentTimeMillis(),
                millis -> Thread.sleep(millis),
                minutes -> log.info("Prompt 1 turn {}: waiting for the answer ({} min)", label, minutes));
        String status = done.status().map(s -> s.asString()).orElse("");
        if (status.equals("failed") || status.equals("cancelled")) {
            throw new IllegalStateException("background response " + status
                    + done.error().map(error -> ": " + error.message()).orElse(""));
        }
        return done;
    }

    /** Waits between two polls (Thread.sleep in the service, a stub in tests). */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /**
     * Polls a background job until its status is final (not queued or in_progress). A failed check is
     * repeated (at most 10 in a row); after {@code maxMillis} the job is cancelled and the turn fails.
     */
    static <T> T poll(String turn, T first, Function<T, T> retrieve, Function<T, String> status, java.util.function.Consumer<T> cancel,
                      long pollMillis, long maxMillis, java.util.function.LongSupplier now, Sleeper sleeper,
                      java.util.function.LongConsumer heartbeat) {
        long start = now.getAsLong();
        long lastBeat = 0;
        int failures = 0;
        T current = first;
        while (List.of("queued", "in_progress").contains(status.apply(current))) {
            long waited = now.getAsLong() - start;
            if (waited >= maxMillis) {
                try {
                    cancel.accept(current);
                } catch (RuntimeException e) {
                    // the job is stopped by its own limit at OpenAI
                }
                throw new TurnFailedException(turn, "no answer after " + (maxMillis / 60_000) + " minutes; the job was cancelled");
            }
            if (waited / 60_000 > lastBeat) {
                lastBeat = waited / 60_000;
                heartbeat.accept(lastBeat);
            }
            try {
                sleeper.sleep(pollMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TurnFailedException(turn, "interrupted");
            }
            try {
                current = retrieve.apply(current);
                failures = 0;
            } catch (RuntimeException e) {
                if (++failures >= 10) {
                    throw e;
                }
            }
        }
        return current;
    }

    /** The call's cost at its own model's prices (prices setting); otherwise at the experiment's price settings. */
    CallUsage withModelCost(String callModel, CallUsage usage) {
        if (parsedPrices == null) {
            parsedPrices = modelPrices(prices);
        }
        double[] p = parsedPrices.get(callModel);
        if (p == null) {
            return experimentService.withCost(usage);
        }
        usage.setEstimatedCostUsd(Prompt1ExperimentService.estimateCostUsd(usage, p[0], p[1], p[2], p[3]));
        return usage;
    }

    /** "model=input/cached/cache-write/output,…" → model → the four prices per 1M tokens. */
    static Map<String, double[]> modelPrices(String spec) {
        Map<String, double[]> out = new HashMap<>();
        if (spec == null || spec.isBlank()) {
            return out;
        }
        for (String entry : spec.split(",")) {
            String[] kv = entry.split("=", 2);
            String[] values = kv.length == 2 ? kv[1].split("/") : new String[0];
            if (values.length != 4) {
                throw new IllegalArgumentException("Price entry '" + entry.strip() + "' not understood; use "
                        + "model=input/cached/cache-write/output, e.g. gpt-6-luna=0.10/0.01/0.125/0.50");
            }
            double[] p = new double[4];
            for (int i = 0; i < 4; i++) {
                p[i] = Double.parseDouble(values[i].strip());
            }
            out.put(kv[0].strip(), p);
        }
        return out;
    }

    private static String outputText(Response response) {
        StringBuilder text = new StringBuilder();
        response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .forEach(content -> content.outputText().ifPresent(output -> text.append(output.text())));
        return text.toString();
    }
}
