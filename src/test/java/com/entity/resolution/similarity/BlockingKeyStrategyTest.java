package com.entity.resolution.similarity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DefaultBlockingKeyStrategy.
 */
class BlockingKeyStrategyTest {

    private DefaultBlockingKeyStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new DefaultBlockingKeyStrategy();
    }

    @Test
    void generateKeys_producesAllThreeKeyTypes() {
        Set<String> keys = strategy.generateKeys("microsoft corporation");

        assertTrue(keys.stream().anyMatch(k -> k.startsWith("pfx:")));
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("tok:")));
        assertTrue(keys.stream().anyMatch(k -> k.startsWith("bg:")));
    }

    @Test
    void generateKeys_prefixKey() {
        Set<String> keys = strategy.generateKeys("microsoft corporation");
        assertTrue(keys.contains("pfx:mic"));
    }

    @Test
    void generateKeys_sortedTokenKey() {
        Set<String> keys = strategy.generateKeys("microsoft corporation");
        // Tokens sorted: "corporation", "microsoft" -> first two sorted
        assertTrue(keys.contains("tok:corporation|microsoft"));
    }

    @Test
    void generateKeys_bigramKey() {
        Set<String> keys = strategy.generateKeys("microsoft corporation");
        assertTrue(keys.contains("bg:mi"));
    }

    @Test
    void generateKeys_singleWord() {
        Set<String> keys = strategy.generateKeys("microsoft");

        assertTrue(keys.contains("pfx:mic"));
        assertTrue(keys.contains("tok:microsoft"));
        assertTrue(keys.contains("bg:mi"));
    }

    @Test
    void generateKeys_shortName() {
        Set<String> keys = strategy.generateKeys("ab");

        assertTrue(keys.contains("pfx:ab"));
        assertTrue(keys.contains("tok:ab"));
        assertTrue(keys.contains("bg:ab"));
    }

    @Test
    void generateKeys_singleChar() {
        Set<String> keys = strategy.generateKeys("a");

        assertTrue(keys.contains("pfx:a"));
        assertTrue(keys.contains("tok:a"));
        assertTrue(keys.contains("bg:a"));
    }

    @Test
    void generateKeys_emptyString_returnsEmpty() {
        Set<String> keys = strategy.generateKeys("");
        assertTrue(keys.isEmpty());
    }

    @Test
    void generateKeys_blankString_returnsEmpty() {
        Set<String> keys = strategy.generateKeys("   ");
        assertTrue(keys.isEmpty());
    }

    @Test
    void generateKeys_null_returnsEmpty() {
        Set<String> keys = strategy.generateKeys(null);
        assertTrue(keys.isEmpty());
    }

    @Test
    void generateKeys_caseInsensitive() {
        Set<String> keysLower = strategy.generateKeys("microsoft");
        Set<String> keysUpper = strategy.generateKeys("MICROSOFT");
        Set<String> keysMixed = strategy.generateKeys("Microsoft");

        assertEquals(keysLower, keysUpper);
        assertEquals(keysLower, keysMixed);
    }

    @Test
    void generateKeys_similarNames_shareKeys() {
        Set<String> keys1 = strategy.generateKeys("microsoft corporation");
        Set<String> keys2 = strategy.generateKeys("microsoft corp");

        // Both should share the prefix key
        assertTrue(keys1.contains("pfx:mic"));
        assertTrue(keys2.contains("pfx:mic"));

        // Both should share the bigram key
        assertTrue(keys1.contains("bg:mi"));
        assertTrue(keys2.contains("bg:mi"));
    }

    @Test
    void generateKeys_differentNames_differentKeys() {
        Set<String> keys1 = strategy.generateKeys("microsoft corporation");
        Set<String> keys2 = strategy.generateKeys("apple inc");

        // Different prefix keys
        assertTrue(keys1.contains("pfx:mic"));
        assertTrue(keys2.contains("pfx:app"));

        // Different bigram keys
        assertTrue(keys1.contains("bg:mi"));
        assertTrue(keys2.contains("bg:ap"));
    }

    @Test
    void generateKeys_multipleTokens_useFirstTwoSorted() {
        Set<String> keys = strategy.generateKeys("acme big corporation");
        // Sorted tokens: "acme", "big", "corporation" -> first two: "acme|big"
        assertTrue(keys.contains("tok:acme|big"));
    }

    @Test
    void generateKeys_leadingTrailingWhitespace_trimmed() {
        Set<String> keys1 = strategy.generateKeys("  microsoft  ");
        Set<String> keys2 = strategy.generateKeys("microsoft");

        assertEquals(keys1, keys2);
    }
}
