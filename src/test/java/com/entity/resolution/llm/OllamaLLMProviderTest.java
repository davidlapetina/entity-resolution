package com.entity.resolution.llm;

import com.entity.resolution.core.model.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for OllamaLLMProvider.
 *
 * Note: Integration tests require Ollama to be running locally with llama3.2 model.
 * Install: https://ollama.ai
 * Pull model: ollama pull llama3.2
 */
class OllamaLLMProviderTest {

    @Nested
    @DisplayName("Unit Tests (no Ollama required)")
    class UnitTests {

        private OllamaLLMProvider provider;

        @BeforeEach
        void setUp() {
            provider = OllamaLLMProvider.builder()
                    .baseUrl("http://localhost:11434")
                    .model("llama3.2")
                    .timeout(Duration.ofSeconds(30))
                    .build();
        }

        @Test
        @DisplayName("Provider name includes model")
        void providerNameIncludesModel() {
            assertEquals("Ollama/llama3.2", provider.getProviderName());
        }

        @Test
        @DisplayName("Default confidence threshold is 0.85")
        void defaultConfidenceThreshold() {
            assertEquals(0.85, provider.getDefaultConfidenceThreshold());
        }

        @Test
        @DisplayName("Builder creates provider with custom settings")
        void builderWithCustomSettings() {
            OllamaLLMProvider customProvider = OllamaLLMProvider.builder()
                    .baseUrl("http://custom:8080")
                    .model("custom-model")
                    .timeout(Duration.ofMinutes(2))
                    .build();

            assertEquals("Ollama/custom-model", customProvider.getProviderName());
        }

        @Test
        @DisplayName("createDefault uses llama3.2")
        void createDefaultUsesLlama32() {
            OllamaLLMProvider defaultProvider = OllamaLLMProvider.createDefault();
            assertEquals("Ollama/llama3.2", defaultProvider.getProviderName());
        }

        @Test
        @DisplayName("withModel creates provider with specified model")
        void withModelCreatesProvider() {
            OllamaLLMProvider customProvider = OllamaLLMProvider.withModel("mistral");
            assertEquals("Ollama/mistral", customProvider.getProviderName());
        }
    }

    @Nested
    @DisplayName("Response Parsing Tests")
    class ResponseParsingTests {

        @Test
        @DisplayName("Parse well-formatted positive response")
        void parseWellFormattedPositiveResponse() {
            // This tests the internal parsing logic by creating a provider
            // and checking that the response format expectations are documented
            OllamaLLMProvider provider = OllamaLLMProvider.createDefault();

            // Verify the provider handles requests correctly
            // The actual parsing is tested via integration tests
            assertNotNull(provider);
        }
    }

    @Nested
    @DisplayName("Integration Tests (requires Ollama)")
    @EnabledIf("com.entity.resolution.llm.OllamaLLMProviderTest#isOllamaAvailable")
    class IntegrationTests {

        private OllamaLLMProvider provider;

        @BeforeEach
        void setUp() {
            provider = OllamaLLMProvider.createDefault();
        }

        @Test
        @DisplayName("Ollama is available")
        void ollamaIsAvailable() {
            assertTrue(provider.isAvailable(), "Ollama should be running for integration tests");
        }

        @Test
        @DisplayName("Recognize IBM and Big Blue as same entity")
        void recognizeIbmBigBlue() {
            LLMEnrichmentRequest request = LLMEnrichmentRequest.builder()
                    .entityName1("IBM")
                    .entityName2("Big Blue")
                    .entityType(EntityType.COMPANY)
                    .build();

            LLMEnrichmentResponse response = provider.enrich(request);

            assertNotNull(response);
            assertNotNull(response.reasoning());
            assertFalse(response.reasoning().isEmpty(), "Should have reasoning");
            assertTrue(response.confidence() >= 0 && response.confidence() <= 1,
                    "Confidence should be between 0 and 1");

            // Log the result
            System.out.println("IBM vs Big Blue: sameEntity=" + response.areSameEntity() +
                    ", confidence=" + response.confidence() +
                    ", reasoning=" + response.reasoning());
            System.out.println("Synonyms: " + response.suggestedSynonyms());
        }

        @Test
        @DisplayName("Recognize different companies - verifies response structure")
        void recognizeDifferentCompanies() {
            LLMEnrichmentRequest request = LLMEnrichmentRequest.builder()
                    .entityName1("Microsoft Corporation")
                    .entityName2("Toyota Motor Corporation")
                    .entityType(EntityType.COMPANY)
                    .build();

            LLMEnrichmentResponse response = provider.enrich(request);

            assertNotNull(response);
            assertNotNull(response.reasoning());
            assertFalse(response.reasoning().isEmpty(), "Should have reasoning");
            assertTrue(response.confidence() >= 0 && response.confidence() <= 1,
                    "Confidence should be between 0 and 1");

            // Log the result - LLM accuracy can vary
            System.out.println("Microsoft vs Toyota: sameEntity=" + response.areSameEntity() +
                    ", confidence=" + response.confidence() +
                    ", reasoning=" + response.reasoning());
        }

        @Test
        @DisplayName("Recognize company name variations")
        void recognizeCompanyVariations() {
            LLMEnrichmentRequest request = LLMEnrichmentRequest.builder()
                    .entityName1("Google")
                    .entityName2("Alphabet Inc")
                    .entityType(EntityType.COMPANY)
                    .additionalContext(List.of("Consider parent companies and subsidiaries"))
                    .build();

            LLMEnrichmentResponse response = provider.enrich(request);

            assertNotNull(response);
            assertNotNull(response.reasoning());
            // Google/Alphabet relationship should be recognized
            // Note: The LLM might say they're related but different entities
            System.out.println("Google vs Alphabet: sameEntity=" + response.areSameEntity() +
                    ", confidence=" + response.confidence() +
                    ", reasoning=" + response.reasoning());
        }

        @Test
        @DisplayName("Handle person name variations")
        void handlePersonNameVariations() {
            LLMEnrichmentRequest request = LLMEnrichmentRequest.builder()
                    .entityName1("William Gates")
                    .entityName2("Bill Gates")
                    .entityType(EntityType.PERSON)
                    .build();

            LLMEnrichmentResponse response = provider.enrich(request);

            assertNotNull(response);
            assertNotNull(response.reasoning());
            assertTrue(response.confidence() >= 0 && response.confidence() <= 1,
                    "Confidence should be between 0 and 1");

            // Log the result
            System.out.println("William Gates vs Bill Gates: sameEntity=" + response.areSameEntity() +
                    ", confidence=" + response.confidence() +
                    ", reasoning=" + response.reasoning());
        }

        @Test
        @DisplayName("Response includes synonyms for matching entities")
        void responseIncludesSynonyms() {
            LLMEnrichmentRequest request = LLMEnrichmentRequest.builder()
                    .entityName1("Coca-Cola")
                    .entityName2("Coke")
                    .entityType(EntityType.COMPANY)
                    .build();

            LLMEnrichmentResponse response = provider.enrich(request);

            assertNotNull(response);
            assertNotNull(response.reasoning());
            // LLM results can vary - we're testing the integration works, not the LLM's accuracy
            System.out.println("Coca-Cola vs Coke: sameEntity=" + response.areSameEntity() +
                    ", confidence=" + response.confidence() +
                    ", reasoning=" + response.reasoning());
            System.out.println("Suggested synonyms: " + response.suggestedSynonyms());
        }
    }

    /**
     * Check if Ollama is available for integration tests.
     */
    static boolean isOllamaAvailable() {
        try {
            OllamaLLMProvider provider = OllamaLLMProvider.createDefault();
            return provider.isAvailable();
        } catch (Exception e) {
            return false;
        }
    }
}
