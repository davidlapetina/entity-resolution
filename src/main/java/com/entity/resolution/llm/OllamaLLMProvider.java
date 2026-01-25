package com.entity.resolution.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM provider implementation using Ollama with llama3.2 model.
 *
 * Ollama must be running locally (default: http://localhost:11434).
 * Install: https://ollama.ai
 * Pull model: ollama pull llama3.2
 *
 * Usage:
 * <pre>
 * OllamaLLMProvider provider = OllamaLLMProvider.builder()
 *     .baseUrl("http://localhost:11434")
 *     .model("llama3.2")
 *     .build();
 *
 * EntityResolver resolver = EntityResolver.builder()
 *     .graphConnection(connection)
 *     .llmProvider(provider)
 *     .options(ResolutionOptions.withLLM())
 *     .build();
 * </pre>
 */
public class OllamaLLMProvider implements LLMProvider {
    private static final Logger log = LoggerFactory.getLogger(OllamaLLMProvider.class);

    private static final String DEFAULT_BASE_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // Patterns for parsing LLM response
    private static final Pattern CONFIDENCE_PATTERN = Pattern.compile(
            "(?i)confidence[:\\s]+([0-9]*\\.?[0-9]+)|([0-9]*\\.?[0-9]+)\\s*(?:confidence|%)"
    );
    private static final Pattern YES_NO_PATTERN = Pattern.compile(
            "(?i)\\b(yes|no|same|different|match|not\\s+match|identical|distinct)\\b"
    );

    private OllamaLLMProvider(Builder builder) {
        this.baseUrl = builder.baseUrl != null ? builder.baseUrl : DEFAULT_BASE_URL;
        this.model = builder.model != null ? builder.model : DEFAULT_MODEL;
        this.timeout = builder.timeout != null ? builder.timeout : DEFAULT_TIMEOUT;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LLMEnrichmentResponse enrich(LLMEnrichmentRequest request) {
        log.info("Enriching entity comparison via Ollama: '{}' vs '{}' (type: {})",
                request.entityName1(), request.entityName2(), request.entityType());

        try {
            String prompt = buildPrompt(request);
            String response = callOllama(prompt);
            return parseResponse(response, request);
        } catch (Exception e) {
            log.error("Error calling Ollama: {}", e.getMessage(), e);
            return LLMEnrichmentResponse.uncertain("Error calling LLM: " + e.getMessage());
        }
    }

    @Override
    public String getProviderName() {
        return "Ollama/" + model;
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Ollama not available: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Builds the prompt for entity comparison.
     */
    private String buildPrompt(LLMEnrichmentRequest request) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("You are an entity resolution expert. Your task is to determine if two names refer to the same ");
        prompt.append(request.entityType().name().toLowerCase());
        prompt.append(".\n\n");

        prompt.append("Entity 1: \"").append(request.entityName1()).append("\"\n");
        prompt.append("Entity 2: \"").append(request.entityName2()).append("\"\n\n");

        if (!request.additionalContext().isEmpty()) {
            prompt.append("Additional context:\n");
            for (String context : request.additionalContext()) {
                prompt.append("- ").append(context).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("Instructions:\n");
        prompt.append("1. Determine if these two names refer to the SAME ").append(request.entityType().name().toLowerCase()).append(" or DIFFERENT ones.\n");
        prompt.append("2. Consider nicknames, abbreviations, translations, brand names, and common aliases.\n");
        prompt.append("3. Provide your reasoning.\n");
        prompt.append("4. Give a confidence score from 0.0 to 1.0.\n");
        prompt.append("5. If they are the same entity, suggest any synonyms or aliases.\n\n");

        prompt.append("Respond in this exact format:\n");
        prompt.append("SAME_ENTITY: [YES/NO]\n");
        prompt.append("CONFIDENCE: [0.0-1.0]\n");
        prompt.append("REASONING: [Your explanation]\n");
        prompt.append("SYNONYMS: [comma-separated list of synonyms, or NONE]\n");

        return prompt.toString();
    }

    /**
     * Calls the Ollama API.
     */
    private String callOllama(String prompt) throws Exception {
        OllamaRequest ollamaRequest = new OllamaRequest(model, prompt, false);
        String requestBody = objectMapper.writeValueAsString(ollamaRequest);

        log.debug("Calling Ollama with model: {}", model);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/generate"))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama returned status " + response.statusCode() + ": " + response.body());
        }

        OllamaResponse ollamaResponse = objectMapper.readValue(response.body(), OllamaResponse.class);
        log.debug("Ollama response received, length: {}", ollamaResponse.response().length());

        return ollamaResponse.response();
    }

    /**
     * Parses the LLM response into a structured LLMEnrichmentResponse.
     */
    private LLMEnrichmentResponse parseResponse(String response, LLMEnrichmentRequest request) {
        log.debug("Parsing LLM response:\n{}", response);

        boolean areSameEntity = parseSameEntity(response);
        double confidence = parseConfidence(response);
        String reasoning = parseReasoning(response);
        List<String> synonyms = parseSynonyms(response, request);

        // Adjust confidence if we couldn't parse it properly
        if (confidence == 0.5 && !response.toLowerCase().contains("uncertain")) {
            // Try to infer confidence from the response tone
            if (areSameEntity && response.toLowerCase().contains("definitely") ||
                    response.toLowerCase().contains("clearly") ||
                    response.toLowerCase().contains("certainly")) {
                confidence = 0.95;
            } else if (areSameEntity) {
                confidence = 0.85;
            } else if (response.toLowerCase().contains("unlikely") ||
                    response.toLowerCase().contains("probably not")) {
                confidence = 0.8;
            }
        }

        log.info("LLM result: sameEntity={}, confidence={}, synonyms={}", areSameEntity, confidence, synonyms);

        return LLMEnrichmentResponse.builder()
                .areSameEntity(areSameEntity)
                .confidence(confidence)
                .reasoning(reasoning)
                .suggestedSynonyms(synonyms)
                .relatedEntities(List.of())
                .build();
    }

    private boolean parseSameEntity(String response) {
        // Look for explicit SAME_ENTITY: YES/NO or SAME ENTITY: YES/NO (with space or underscore)
        Pattern sameEntityPattern = Pattern.compile("(?i)SAME[_\\s]ENTITY:\\s*(YES|NO)");
        Matcher matcher = sameEntityPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).equalsIgnoreCase("YES");
        }

        // Fall back to looking for yes/no patterns
        String lowerResponse = response.toLowerCase();

        // Positive indicators
        if (lowerResponse.contains("yes, they are the same") ||
                lowerResponse.contains("same entity") ||
                lowerResponse.contains("same company") ||
                lowerResponse.contains("same person") ||
                lowerResponse.contains("refer to the same") ||
                lowerResponse.contains("are identical") ||
                lowerResponse.contains("is a nickname for") ||
                lowerResponse.contains("is an alias for") ||
                lowerResponse.contains("commonly known as")) {
            return true;
        }

        // Negative indicators
        if (lowerResponse.contains("no, they are") ||
                lowerResponse.contains("different entities") ||
                lowerResponse.contains("different companies") ||
                lowerResponse.contains("not the same") ||
                lowerResponse.contains("are distinct")) {
            return false;
        }

        // Check for simple yes at the beginning
        if (lowerResponse.startsWith("yes")) {
            return true;
        }

        return false;
    }

    private double parseConfidence(String response) {
        // Look for explicit CONFIDENCE: X.XX
        Pattern confidencePattern = Pattern.compile("(?i)CONFIDENCE:\\s*([0-9]*\\.?[0-9]+)");
        Matcher matcher = confidencePattern.matcher(response);
        if (matcher.find()) {
            try {
                double conf = Double.parseDouble(matcher.group(1));
                // Handle percentage format
                if (conf > 1.0 && conf <= 100.0) {
                    conf = conf / 100.0;
                }
                return Math.min(1.0, Math.max(0.0, conf));
            } catch (NumberFormatException e) {
                log.debug("Could not parse confidence: {}", matcher.group(1));
            }
        }

        // Look for percentage patterns
        Pattern percentPattern = Pattern.compile("([0-9]+)%");
        matcher = percentPattern.matcher(response);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1)) / 100.0;
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Default to 0.5 (uncertain)
        return 0.5;
    }

    private String parseReasoning(String response) {
        // Look for explicit REASONING:
        Pattern reasoningPattern = Pattern.compile("(?i)REASONING:\\s*(.+?)(?=SYNONYMS:|$)", Pattern.DOTALL);
        Matcher matcher = reasoningPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // Fall back to using the entire response as reasoning (truncated)
        String reasoning = response.replaceAll("(?i)SAME_ENTITY:.*?\\n", "")
                .replaceAll("(?i)CONFIDENCE:.*?\\n", "")
                .replaceAll("(?i)SYNONYMS:.*", "")
                .trim();

        if (reasoning.length() > 500) {
            reasoning = reasoning.substring(0, 500) + "...";
        }

        return reasoning.isEmpty() ? "No explicit reasoning provided" : reasoning;
    }

    private List<String> parseSynonyms(String response, LLMEnrichmentRequest request) {
        List<String> synonyms = new ArrayList<>();

        // Look for explicit SYNONYMS:
        Pattern synonymsPattern = Pattern.compile("(?i)SYNONYMS:\\s*(.+?)(?=\\n|$)");
        Matcher matcher = synonymsPattern.matcher(response);
        if (matcher.find()) {
            String synonymStr = matcher.group(1).trim();
            if (!synonymStr.equalsIgnoreCase("NONE") && !synonymStr.isEmpty()) {
                // Split by comma and clean up
                for (String syn : synonymStr.split(",")) {
                    String cleaned = syn.trim()
                            .replaceAll("^[\"']|[\"']$", "") // Remove quotes
                            .replaceAll("^-\\s*", ""); // Remove leading dash
                    if (!cleaned.isEmpty() &&
                            !cleaned.equalsIgnoreCase(request.entityName1()) &&
                            !cleaned.equalsIgnoreCase(request.entityName2())) {
                        synonyms.add(cleaned);
                    }
                }
            }
        }

        // Also check for common patterns in the text
        String lowerResponse = response.toLowerCase();
        if (lowerResponse.contains("also known as") || lowerResponse.contains("aka")) {
            Pattern akaPattern = Pattern.compile("(?i)(?:also known as|aka)\\s*[\"']?([^\"',.]+)[\"']?");
            matcher = akaPattern.matcher(response);
            while (matcher.find()) {
                String syn = matcher.group(1).trim();
                if (!syn.isEmpty() && !synonyms.contains(syn)) {
                    synonyms.add(syn);
                }
            }
        }

        return synonyms;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a default Ollama provider with llama3.2.
     */
    public static OllamaLLMProvider createDefault() {
        return builder().build();
    }

    /**
     * Creates an Ollama provider with a custom model.
     */
    public static OllamaLLMProvider withModel(String model) {
        return builder().model(model).build();
    }

    public static class Builder {
        private String baseUrl;
        private String model;
        private Duration timeout;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public OllamaLLMProvider build() {
            return new OllamaLLMProvider(this);
        }
    }

    // Request/Response DTOs for Ollama API
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaRequest(
            String model,
            String prompt,
            boolean stream
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaResponse(
            String model,
            @JsonProperty("created_at") String createdAt,
            String response,
            boolean done
    ) {}
}
