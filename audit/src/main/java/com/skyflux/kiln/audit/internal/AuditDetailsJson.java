package com.skyflux.kiln.audit.internal;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Gate 3 H4: centralises serialisation of the {@code details} JSON column
 * on {@code audit_events}. Replaces hand-built string concatenation —
 * unescaped quotes / backslashes / control chars in source strings
 * (notably the user-supplied email on {@code USER_REGISTERED}) would
 * otherwise produce malformed JSON, rejected by PG's JSONB parser,
 * causing the audit row to be silently lost.
 *
 * <p>One shared {@link JsonMapper} instance: the Jackson 3 JsonMapper is
 * thread-safe after configuration and this class only invokes
 * {@link JsonMapper#writeValueAsString} which does not mutate state.
 */
final class AuditDetailsJson {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private AuditDetailsJson() {
    }

    /**
     * Serialises {@code keyValuePairs} as a compact JSON object. Returns
     * {@code null} when the map is empty — keeps {@code details} NULL in
     * the DB rather than writing an uninformative {@code "{}"}.
     */
    static String from(Map<String, ?> keyValuePairs) {
        if (keyValuePairs == null || keyValuePairs.isEmpty()) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(keyValuePairs);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "failed to serialise audit details: " + keyValuePairs.keySet(), e);
        }
    }
}
