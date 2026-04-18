package com.skyflux.kiln.member.internal;

import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import com.skyflux.kiln.user.domain.event.UserRegistered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class UserRegisteredMemberListener {

    private final MemberJooqRepository memberRepo;

    UserRegisteredMemberListener(MemberJooqRepository repo) {
        this.memberRepo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void on(UserRegistered event) {
        Member member = Member.create(event.tenantId(), event.userId().value());
        memberRepo.save(member);
    }
}
