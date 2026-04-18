package com.skyflux.kiln.audit.internal;

import com.skyflux.kiln.audit.api.AuditService;
import com.skyflux.kiln.audit.domain.AuditAction;
import com.skyflux.kiln.audit.domain.AuditResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock AuditService auditService;
    @InjectMocks AuditAspect aspect;

    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }

    private void setRequest(String method, String path) {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test void shouldMapPostOrdersToOrderCreate() {
        setRequest("POST", "/api/v1/orders");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.CREATE),
            any(), any(), any(), any());
    }

    @Test void shouldMapGetOrdersToOrderRead() {
        setRequest("GET", "/api/v1/orders");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.READ),
            any(), any(), any(), any());
    }

    @Test void shouldMapPostLoginToUserLogin() {
        setRequest("POST", "/api/v1/auth/login");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.USER), eq(AuditAction.LOGIN),
            any(), any(), any(), any());
    }

    @Test void shouldMapDeleteProductToProductDelete() {
        setRequest("DELETE", "/api/v1/products/some-uuid");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.PRODUCT), eq(AuditAction.DELETE),
            any(), any(), any(), any());
    }

    @Test void shouldMapPostPayToOrderPay() {
        setRequest("POST", "/api/v1/orders/some-uuid/pay");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.PAY),
            any(), any(), any(), any());
    }

    @Test void shouldMapPutUserToUserUpdate() {
        setRequest("PUT", "/api/v1/users/some-uuid");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.USER), eq(AuditAction.UPDATE),
            any(), any(), any(), any());
    }

    @Test void shouldMapPostConfirmToOrderUpdate() {
        setRequest("POST", "/api/v1/orders/some-uuid/confirm");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.UPDATE),
            any(), any(), any(), any());
    }

    @Test void shouldMapPostShipToOrderUpdate() {
        setRequest("POST", "/api/v1/orders/some-uuid/ship");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.UPDATE),
            any(), any(), any(), any());
    }

    @Test void shouldMapPostDeliverToOrderUpdate() {
        setRequest("POST", "/api/v1/orders/some-uuid/deliver");
        aspect.recordSuccess(null);
        verify(auditService).record(eq(AuditResource.ORDER), eq(AuditAction.UPDATE),
            any(), any(), any(), any());
    }

    @Test void shouldNoOpWhenNoRequestContext() {
        // No request set — should not throw and should not call auditService
        RequestContextHolder.resetRequestAttributes();
        aspect.recordSuccess(null);
        verifyNoInteractions(auditService);
    }
}
