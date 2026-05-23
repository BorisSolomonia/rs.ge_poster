package ge.camora.erp.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StringUtilTest {

    @Test
    void testIdenticalStringsHaveDistanceZero() {
        assertEquals(0, StringUtil.levenshtein("hello", "hello"));
        assertEquals(0, StringUtil.levenshtein("", ""));
    }

    @Test
    void testDifferentStringsDistance() {
        assertEquals(1, StringUtil.levenshtein("hello", "hell"));
        assertEquals(1, StringUtil.levenshtein("hell", "hello"));
        assertEquals(1, StringUtil.levenshtein("hello", "hallo"));
        assertEquals(3, StringUtil.levenshtein("kitten", "sitting"));
        assertEquals(4, StringUtil.levenshtein("hello", "world"));
    }

    @Test
    void testEmptyStringDistance() {
        assertEquals(5, StringUtil.levenshtein("", "hello"));
        assertEquals(5, StringUtil.levenshtein("hello", ""));
    }

    @Test
    void testNullStringsThrowException() {
        assertThrows(IllegalArgumentException.class, () -> StringUtil.levenshtein(null, "hello"));
        assertThrows(IllegalArgumentException.class, () -> StringUtil.levenshtein("hello", null));
        assertThrows(IllegalArgumentException.class, () -> StringUtil.levenshtein(null, null));
    }
}
