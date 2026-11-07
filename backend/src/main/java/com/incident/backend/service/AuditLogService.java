package com.incident.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.backend.events.IncidentAuditEvent;
import com.incident.backend.model.AuditLog;
import com.incident.backend.repository.AuditLogRepository;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class AuditLogService {
    private final AuditLogRepository auditLogRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final IncidentAccess access;
    private final ObjectMapper objectMapper;

    public AuditLogService(AuditLogRepository auditLogRepository, IncidentRepository incidentRepository,
                           UserRepository userRepository, IncidentAccess access, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.objectMapper = objectMapper;
    }

    public AuditLog record(IncidentAuditEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Incident audit events must be handled inside the business transaction");
        }
        try {
            String oldValue = event.oldValues().isEmpty() ? null : objectMapper.writeValueAsString(event.oldValues());
            String newValue = event.newValues().isEmpty() ? null : objectMapper.writeValueAsString(event.newValues());
            AuditLog log = new AuditLog(
                    incidentRepository.getReferenceById(event.incidentId()),
                    event.actorUserId() == null ? null : userRepository.getReferenceById(event.actorUserId()),
                    event.action(), oldValue, newValue, event.description(), event.occurredAt());
            return auditLogRepository.saveAndFlush(log);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize audit event snapshot", ex);
        }
    }

    public List<AuditLog> history(Long incidentId, com.incident.backend.model.User actor) {
        access.requireViewable(incidentId, actor);
        return auditLogRepository.findByIncidentIdOrderByTimestampAscIdAsc(Math.toIntExact(incidentId));
    }
}
