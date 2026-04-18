package com.skyflux.kiln.member.internal;

import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.member.domain.Member;
import com.skyflux.kiln.member.repo.MemberJooqRepository;
import com.skyflux.kiln.user.domain.event.UserRegistered;
import com.skyflux.kiln.user.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegisteredMemberListenerTest {

    @Mock MemberJooqRepository memberRepo;
    @InjectMocks UserRegisteredMemberListener listener;

    @Test void shouldCreateMemberOnUserRegistration() {
        UUID userId = Ids.next();
        UUID tenantId = Ids.next();
        UserRegistered event = UserRegistered.of(new UserId(userId), tenantId, "u@example.com");

        listener.on(event);

        ArgumentCaptor<Member> captor = ArgumentCaptor.forClass(Member.class);
        verify(memberRepo).save(captor.capture());
        Member saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(userId);
        assertThat(saved.tenantId()).isEqualTo(tenantId);
        assertThat(saved.points()).isZero();
    }
}
