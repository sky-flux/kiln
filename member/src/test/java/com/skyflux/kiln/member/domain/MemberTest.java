package com.skyflux.kiln.member.domain;

import com.skyflux.kiln.common.util.Ids;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MemberTest {
    @Test void shouldCreateWithBronzeAndZeroPoints() {
        Member m = Member.create(Ids.next(), Ids.next());
        assertThat(m.level()).isEqualTo(MemberLevel.BRONZE);
        assertThat(m.points()).isZero();
        assertThat(m.status()).isEqualTo("ACTIVE");
    }
    @Test void shouldAwardPoints() {
        Member m = Member.create(Ids.next(), Ids.next()).awardPoints(100);
        assertThat(m.points()).isEqualTo(100);
    }
    @Test void shouldRejectNegativePoints() {
        assertThatIllegalArgumentException()
            .isThrownBy(() -> Member.create(Ids.next(), Ids.next()).awardPoints(-1));
    }
    @Test void shouldUpgradeToSilverAt1000Points() {
        Member m = Member.create(Ids.next(), Ids.next()).awardPoints(1000);
        assertThat(m.level()).isEqualTo(MemberLevel.SILVER);
    }
    @Test void shouldUpgradeToGoldAt5000Points() {
        Member m = Member.create(Ids.next(), Ids.next()).awardPoints(5000);
        assertThat(m.level()).isEqualTo(MemberLevel.GOLD);
    }
    @Test void shouldRemainGoldAbove5000() {
        Member m = Member.create(Ids.next(), Ids.next()).awardPoints(10000);
        assertThat(m.level()).isEqualTo(MemberLevel.GOLD);
    }
}
