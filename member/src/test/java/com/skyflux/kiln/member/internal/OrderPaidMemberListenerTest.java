package com.skyflux.kiln.member.internal;

import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.domain.MemberId;
import com.skyflux.kiln.member.domain.MemberLevel;
import com.skyflux.kiln.member.api.MemberEvent;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import com.skyflux.kiln.order.api.OrderEvent;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderPaidMemberListenerTest {

    @Mock MemberJooqRepository memberRepo;
    @Mock ApplicationEventPublisher events;
    @Mock Clock clock;
    @InjectMocks OrderPaidMemberListener listener;

    @Test void shouldAwardPointsEqualToOrderAmountRoundedDown() {
        UUID userId = Ids.next();
        UUID tenantId = Ids.next();
        Member member = Member.reconstitute(MemberId.newId(), tenantId, userId,
            MemberLevel.BRONZE, 0, "ACTIVE");
        when(memberRepo.findByUserId(userId)).thenReturn(Optional.of(member));

        listener.on(new OrderEvent.OrderPaid(
            OrderId.newId(), tenantId, userId, Money.of("250.75", "CNY"), Instant.now()));

        verify(memberRepo).save(argThat(m -> m.points() == 250));
    }

    @Test void shouldPublishMemberEventAfterAwardingPoints() {
        UUID userId = Ids.next();
        UUID tenantId = Ids.next();
        Instant fixedNow = Instant.parse("2026-04-18T10:00:00Z");
        when(clock.instant()).thenReturn(fixedNow);
        Member member = Member.reconstitute(MemberId.newId(), tenantId, userId,
            MemberLevel.BRONZE, 0, "ACTIVE");
        when(memberRepo.findByUserId(userId)).thenReturn(Optional.of(member));

        listener.on(new OrderEvent.OrderPaid(
            OrderId.newId(), tenantId, userId, Money.of("100.00", "CNY"), Instant.now()));

        ArgumentCaptor<MemberEvent.PointsAwarded> cap = ArgumentCaptor.forClass(MemberEvent.PointsAwarded.class);
        verify(events).publishEvent(cap.capture());
        assertThat(cap.getValue().userId()).isEqualTo(userId);
        assertThat(cap.getValue().points()).isEqualTo(100);
        assertThat(cap.getValue().occurredAt()).isEqualTo(fixedNow);
    }

    @Test void shouldNoOpWhenMemberNotFound() {
        UUID userId = Ids.next();
        when(memberRepo.findByUserId(userId)).thenReturn(Optional.empty());

        listener.on(new OrderEvent.OrderPaid(
            OrderId.newId(), Ids.next(), userId, Money.of("100.00", "CNY"), Instant.now()));

        verify(memberRepo, never()).save(any());
        verify(events, never()).publishEvent(any());
    }
}
