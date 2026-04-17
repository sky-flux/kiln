package com.skyflux.kiln.user.adapter.in.web;

/** Wire DTO returned from write endpoints — just the newly assigned identifier. */
public record UserIdResponse(String id) {
}
