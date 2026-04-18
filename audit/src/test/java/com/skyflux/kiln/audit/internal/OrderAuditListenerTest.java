package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.common.money.Money;
import com.skyflux.kiln.common.util.Ids;
import com.skyflux.kiln.order.api.OrderEvent;
import com.skyflux.kiln.order.domain.model.OrderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderAuditListenerTest {

    @Mock AuditService auditService;
    @InjectMocks OrderAuditListener listener;

    @Test void shouldAuditOrderCreated() {
        listener.onCreated(new OrderEvent.OrderCreated(
            OrderId.newId(), Ids.next(), Ids.next(), Instant.now()));
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.CREATE),
            any(), isNull(), contains("orderId"), isNull());
    }

    @Test void shouldAuditOrderPaid() {
        listener.onPaid(new OrderEvent.OrderPaid(
            OrderId.newId(), Ids.next(), Ids.next(), Money.of("100.00", "CNY"), Instant.now()));
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.PAY),
            any(), isNull(), contains("orderId"), isNull());
    }

    @Test void shouldAuditOrderCancelled() {
        listener.onCancelled(new OrderEvent.OrderCancelled(
            OrderId.newId(), Ids.next(), Instant.now()));
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.CANCEL),
            isNull(), isNull(), contains("orderId"), isNull());
    }
}
