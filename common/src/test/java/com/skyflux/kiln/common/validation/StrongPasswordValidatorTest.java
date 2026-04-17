package com.skyflux.kiln.common.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    @ParameterizedTest(name = "[{index}] input={0} expected={1}")
    @CsvSource(value = {
            "null, true",                // null -> accept (delegated to @NotBlank)
            "'', false",                 // empty -> reject (< 10 chars)
            "Pass1!, false",             // 6 chars -> reject
            "passworddd, false",         // 10 chars but all letters
            "1234567890, false",         // 10 chars but all digits (no letter)
            "Abcdefghij1, true",         // 11 chars, letter + digit
            "Password-1, true",          // punctuation + letter + digit
            "aaaaaaaaaa1, true",         // min-sized valid (11 chars)
            "Ab1234567, false",          // 9 chars -> reject
    }, nullValues = "null")
    void validatesStrengthRules(String input, boolean expected) {
        assertThat(validator.isValid(input, null)).isEqualTo(expected);
    }

    @Test
    void rejectsOver128Chars() {
        // 130 chars: 129 letters + 1 digit
        String tooLong = "a".repeat(129) + "1";
        assertThat(tooLong).hasSize(130);
        assertThat(validator.isValid(tooLong, null)).isFalse();
    }

    @Test
    void accepts128Chars() {
        // exactly 128 chars: 127 letters + 1 digit
        String maxLen = "a".repeat(127) + "1";
        assertThat(maxLen).hasSize(128);
        assertThat(validator.isValid(maxLen, null)).isTrue();
    }

    @Test
    void accepts10CharBoundary() {
        // exactly 10 chars: 9 letters + 1 digit -> minimum valid length
        String tenChars = "aaaaaaaaa1";
        assertThat(tenChars).hasSize(10);
        assertThat(validator.isValid(tenChars, null)).isTrue();
    }

    @Test
    void acceptsWhitespaceAsNonLetter() {
        // 10 letters + 1 space (11 chars) -> space counts as non-letter.
        // Documented choice: simpler rule; weak short passwords still caught by length.
        String withSpace = "abcdefghij ";
        assertThat(withSpace).hasSize(11);
        assertThat(validator.isValid(withSpace, null)).isTrue();
    }

    @Test
    void rejectsAllLettersAtValidLength() {
        // 15 letters, no digits/symbols -> must still reject (no non-letter).
        String allLetters = "abcdefghijklmno";
        assertThat(allLetters).hasSize(15);
        assertThat(validator.isValid(allLetters, null)).isFalse();
    }

    @Test
    void rejectsAllDigitsAtValidLength() {
        // 15 digits, no letter -> must reject (no letter).
        String allDigits = "123456789012345";
        assertThat(allDigits).hasSize(15);
        assertThat(validator.isValid(allDigits, null)).isFalse();
    }
}
