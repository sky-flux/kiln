package com.skyflux.kiln.infra.security;

import com.skyflux.kiln.common.security.PasswordService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TDD — written BEFORE Argon2idPasswordService.java. Covers null/blank
 * input rejection, Argon2id output format, salt randomness, roundtrip,
 * and constant-time-ish behavior on mismatches.
 */
class Argon2idPasswordServiceTest {

    private final PasswordService service = new Argon2idPasswordService();

    // ──────────── input validation ────────────

    @Test
    void hashRejectsNullPlaintext() {
        assertThatThrownBy(() -> service.hash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashRejectsEmptyPlaintext() {
        assertThatThrownBy(() -> service.hash(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hashRejectsBlankPlaintext() {
        assertThatThrownBy(() -> service.hash("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyRejectsNullPlaintext() {
        assertThatThrownBy(() -> service.verify(null, "$argon2id$v=19$m=19456,t=2,p=1$AAAA$BBBB"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyRejectsBlankPlaintext() {
        assertThatThrownBy(() -> service.verify("  ", "$argon2id$v=19$m=19456,t=2,p=1$AAAA$BBBB"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyRejectsNullEncodedHash() {
        assertThatThrownBy(() -> service.verify("password", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verifyRejectsBlankEncodedHash() {
        assertThatThrownBy(() -> service.verify("password", "   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ──────────── output format (PHC string) ────────────

    @Test
    void hashOutputStartsWithArgon2idHeader() {
        String encoded = service.hash("correct horse battery staple");
        assertThat(encoded).startsWith("$argon2id$v=19$");
    }

    @Test
    void hashOutputEncodesOwaspParameters() {
        String encoded = service.hash("correct horse battery staple");
        // OWASP 2024+: m=19456 (19 MiB), t=2, p=1
        assertThat(encoded).contains("m=19456,t=2,p=1");
    }

    // ──────────── random salt ────────────

    @Test
    void twoHashesOfSameInputDiffer() {
        String a = service.hash("same-plaintext");
        String b = service.hash("same-plaintext");
        assertThat(a).isNotEqualTo(b);
    }

    // ──────────── roundtrip ────────────

    @Test
    void verifyReturnsTrueForMatchingPlaintext() {
        String encoded = service.hash("correct horse battery staple");
        assertThat(service.verify("correct horse battery staple", encoded)).isTrue();
    }

    @Test
    void verifyReturnsFalseForMismatchedPlaintext() {
        String encoded = service.hash("right");
        assertThat(service.verify("wrong", encoded)).isFalse();
    }

    // ──────────── malformed hash → false (not exception) ────────────

    @Test
    void verifyReturnsFalseForMalformedHash() {
        assertThat(service.verify("password", "not-a-valid-hash")).isFalse();
    }

    @Test
    void verifyReturnsFalseForUnknownAlgorithm() {
        assertThat(service.verify("password", "$argon2d$v=19$m=19456,t=2,p=1$AAAA$BBBB")).isFalse();
    }

    @Test
    void verifyReturnsFalseForGarbledBase64() {
        assertThat(service.verify("password", "$argon2id$v=19$m=19456,t=2,p=1$!!!$@@@")).isFalse();
    }

    // ──────────── constant-time sanity: returns false regardless of mismatch position ────────────

    @Test
    void verifyRejectsWhenFirstByteDiffers() {
        // Hash once, then manipulate the final base64 digest byte-wise.
        String encoded = service.hash("correct horse battery staple");
        String mutated = mutateDigestFirstChar(encoded);
        assertThat(service.verify("correct horse battery staple", mutated)).isFalse();
    }

    @Test
    void verifyRejectsWhenLastByteDiffers() {
        String encoded = service.hash("correct horse battery staple");
        String mutated = mutateDigestLastChar(encoded);
        assertThat(service.verify("correct horse battery staple", mutated)).isFalse();
    }

    // helpers

    private static String mutateDigestFirstChar(String encoded) {
        int lastDollar = encoded.lastIndexOf('$');
        String prefix = encoded.substring(0, lastDollar + 1);
        String digest = encoded.substring(lastDollar + 1);
        char c = digest.charAt(0);
        char swapped = (c == 'A') ? 'B' : 'A';
        return prefix + swapped + digest.substring(1);
    }

    private static String mutateDigestLastChar(String encoded) {
        int len = encoded.length();
        char c = encoded.charAt(len - 1);
        char swapped = (c == 'A') ? 'B' : 'A';
        return encoded.substring(0, len - 1) + swapped;
    }
}
