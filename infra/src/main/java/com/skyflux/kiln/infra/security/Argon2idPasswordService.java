package com.skyflux.kiln.infra.security;

import com.skyflux.kiln.common.security.PasswordService;
import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Argon2id password hasher backed by BouncyCastle's pure-Java implementation.
 *
 * <p>Output is the standard PHC-encoded string
 * {@code $argon2id$v=19$m=<memKiB>,t=<iter>,p=<parallelism>$<saltB64>$<hashB64>}
 * so it can roundtrip through a single {@code TEXT} column without a side table.
 *
 * <p>Parameters per OWASP Password Storage Cheat Sheet (2024+ minimum for
 * Argon2id): memory=19 MiB, iterations=2, parallelism=1, 16-byte salt, 32-byte
 * digest.
 *
 * <p>Verification uses {@link MessageDigest#isEqual(byte[], byte[])} for
 * constant-time comparison and swallows {@link IllegalArgumentException} from
 * malformed encoded strings — a corrupted value must return {@code false},
 * never throw.
 */
@Service
class Argon2idPasswordService implements PasswordService {

    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int MEMORY_KIB = 19_456;   // 19 MiB
    private static final int ITERATIONS = 2;
    private static final int PARALLELISM = 1;

    private static final String HEADER_PREFIX = "$argon2id$v=19$";

    private static final Base64.Encoder B64_ENC = Base64.getEncoder().withoutPadding();
    private static final Base64.Decoder B64_DEC = Base64.getDecoder();

    private final SecureRandom random = new SecureRandom();

    @Override
    public String hash(String plaintext) {
        requireNonBlank(plaintext, "plaintext");
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] digest = argon2id(
                plaintext.getBytes(StandardCharsets.UTF_8),
                salt, MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_BYTES);
        return encode(MEMORY_KIB, ITERATIONS, PARALLELISM, salt, digest);
    }

    @Override
    public boolean verify(String plaintext, String encodedHash) {
        requireNonBlank(plaintext, "plaintext");
        requireNonBlank(encodedHash, "encodedHash");
        Decoded d;
        try {
            d = decode(encodedHash);
        } catch (IllegalArgumentException malformed) {
            return false;   // unknown/garbled hash → never matches
        }
        byte[] computed = argon2id(
                plaintext.getBytes(StandardCharsets.UTF_8),
                d.salt, d.memory, d.iterations, d.parallelism, d.hash.length);
        return MessageDigest.isEqual(computed, d.hash);
    }

    // ──────────── helpers ────────────

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be null or blank");
        }
    }

    private static byte[] argon2id(byte[] password, byte[] salt,
                                   int memoryKiB, int iterations, int parallelism, int outputLen) {
        Argon2Parameters params = new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memoryKiB)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build();
        Argon2BytesGenerator gen = new Argon2BytesGenerator();
        gen.init(params);
        byte[] out = new byte[outputLen];
        gen.generateBytes(password, out);
        return out;
    }

    private static String encode(int memoryKiB, int iterations, int parallelism,
                                 byte[] salt, byte[] digest) {
        return HEADER_PREFIX
                + "m=" + memoryKiB + ",t=" + iterations + ",p=" + parallelism
                + "$" + B64_ENC.encodeToString(salt)
                + "$" + B64_ENC.encodeToString(digest);
    }

    /**
     * Parse {@code $argon2id$v=19$m=<m>,t=<t>,p=<p>$<saltB64>$<hashB64>}.
     * Throws {@link IllegalArgumentException} for any deviation (callers in
     * {@link #verify} translate this to {@code false}).
     */
    private static Decoded decode(String encoded) {
        // Expected: ["", "argon2id", "v=19", "m=..,t=..,p=..", "<saltB64>", "<hashB64>"]
        String[] parts = encoded.split("\\$");
        if (parts.length != 6) {
            throw new IllegalArgumentException("invalid encoded hash (segments)");
        }
        if (!"argon2id".equals(parts[1])) {
            throw new IllegalArgumentException("unsupported algorithm: " + parts[1]);
        }
        if (!"v=19".equals(parts[2])) {
            throw new IllegalArgumentException("unsupported version: " + parts[2]);
        }
        String[] paramTokens = parts[3].split(",");
        if (paramTokens.length != 3) {
            throw new IllegalArgumentException("invalid parameter block: " + parts[3]);
        }
        int m = parseKv(paramTokens[0], "m");
        int t = parseKv(paramTokens[1], "t");
        int p = parseKv(paramTokens[2], "p");
        byte[] salt = B64_DEC.decode(parts[4]);
        byte[] hash = B64_DEC.decode(parts[5]);
        return new Decoded(m, t, p, salt, hash);
    }

    private static int parseKv(String token, String expectedKey) {
        int eq = token.indexOf('=');
        if (eq <= 0 || !expectedKey.equals(token.substring(0, eq))) {
            throw new IllegalArgumentException("expected '" + expectedKey + "=...' got '" + token + "'");
        }
        try {
            return Integer.parseInt(token.substring(eq + 1));
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("not a number: " + token, nfe);
        }
    }

    private record Decoded(int memory, int iterations, int parallelism, byte[] salt, byte[] hash) {}
}
