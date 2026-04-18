package com.skyflux.kiln.member.domain;

import com.skyflux.kiln.common.util.Ids;
import java.util.Objects;
import java.util.UUID;

public record MemberId(UUID value) {
    public MemberId {
        Objects.requireNonNull(value, "value");
    }
    public static MemberId newId() { return new MemberId(Ids.next()); }
    public static MemberId of(String s) { return new MemberId(UUID.fromString(s)); }
}
