package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import com.skyflux.kiln.order.api.OrderEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Component
class OrderAuditListener {

    private final AuditService auditService;

    OrderAuditListener(AuditService auditService) { this.auditService = auditService; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCreated(OrderEvent.OrderCreated e) {
        auditService.record(AuditResource.ORDER, AuditAction.CREATE,
            e.userId(), null,
            AuditDetailsJson.from(Map.of("orderId", e.orderId().value().toString())), null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onPaid(OrderEvent.OrderPaid e) {
        auditService.record(AuditResource.ORDER, AuditAction.PAY,
            e.userId(), null,
            AuditDetailsJson.from(Map.of(
                "orderId", e.orderId().value().toString(),
                "amount", e.amount().amount().toPlainString())),
            null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onCancelled(OrderEvent.OrderCancelled e) {
        auditService.record(AuditResource.ORDER, AuditAction.CANCEL,
            null, null,
            AuditDetailsJson.from(Map.of("orderId", e.orderId().value().toString())), null);
    }
}
