package com.skyflux.kiln.user.adapter.in.web;

import com.skyflux.kiln.user.domain.model.User;

/** Wire DTO for {@link User} returned by the REST API. */
public record UserResponse(String id, String name, String email) {
    public static UserResponse from(User u) {
        return new UserResponse(u.id().value().toString(), u.name(), u.email());
    }
}
