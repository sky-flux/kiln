package com.skyflux.kiln.member.domain;

import java.util.Objects;
import java.util.UUID;

public final class Member {
    private final MemberId id;
    private final UUID tenantId;
    private final UUID userId;
    private final MemberLevel level;
    private final int points;
    private final String status;

    private Member(MemberId id, UUID tenantId, UUID userId,
                   MemberLevel level, int points, String status) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.userId = Objects.requireNonNull(userId);
        this.level = Objects.requireNonNull(level);
        this.points = points;
        this.status = Objects.requireNonNull(status);
        if (points < 0) throw new IllegalArgumentException("points negative");
    }

    public static Member create(UUID tenantId, UUID userId) {
        return new Member(MemberId.newId(), tenantId, userId, MemberLevel.BRONZE, 0, "ACTIVE");
    }

    public static Member reconstitute(MemberId id, UUID tenantId, UUID userId,
                                      MemberLevel level, int points, String status) {
        return new Member(id, tenantId, userId, level, points, status);
    }

    public Member awardPoints(int pts) {
        if (pts < 0) throw new IllegalArgumentException("pts must be >= 0");
        int newTotal = this.points + pts;
        return new Member(id, tenantId, userId, MemberLevel.forPoints(newTotal), newTotal, status);
    }

    public MemberId id()       { return id; }
    public UUID tenantId()     { return tenantId; }
    public UUID userId()       { return userId; }
    public MemberLevel level() { return level; }
    public int points()        { return points; }
    public String status()     { return status; }
}
