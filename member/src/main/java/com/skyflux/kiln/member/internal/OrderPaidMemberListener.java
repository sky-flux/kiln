package com.skyflux.kiln.member.internal;

import com.skyflux.kiln.member.api.MemberEvent;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import com.skyflux.kiln.order.api.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.util.Optional;

@Component
class OrderPaidMemberListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaidMemberListener.class);

    private final MemberJooqRepository memberRepo;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    OrderPaidMemberListener(MemberJooqRepository memberRepo, ApplicationEventPublisher events, Clock clock) {
        this.memberRepo = memberRepo;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(OrderEvent.OrderPaid event) {
        Optional<Member> memberOpt = memberRepo.findByUserId(event.userId());
        if (memberOpt.isEmpty()) {
            log.warn("OrderPaidMemberListener: no member found for userId={}, skipping points award",
                event.userId());
            return;
        }
        int points = event.amount().amount().intValue();
        Member updated = memberOpt.get().awardPoints(points);
        memberRepo.save(updated);
        events.publishEvent(new MemberEvent.PointsAwarded(
            event.userId(), event.tenantId(), points, clock.instant()));
    }
}
