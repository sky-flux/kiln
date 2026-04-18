package com.skyflux.kiln.member.domain;

public enum MemberLevel {
    BRONZE(0), SILVER(1000), GOLD(5000);
    private final int minPoints;
    MemberLevel(int min) { this.minPoints = min; }
    public static MemberLevel forPoints(int pts) {
        if (pts >= GOLD.minPoints) return GOLD;
        if (pts >= SILVER.minPoints) return SILVER;
        return BRONZE;
    }
}
