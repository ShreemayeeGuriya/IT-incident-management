package com.incident.backend.events;

import com.incident.backend.service.AuditLogService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditLogEventListener {
    private final AuditLogService auditLogService;

    public AuditLogEventListener(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @EventListener
    public void onIncidentAuditEvent(IncidentAuditEvent event) {
        auditLogService.record(event);
    }
}
