package com.incident.backend.dto;

import com.incident.backend.model.AuditLog;
import java.time.Instant;

public record AuditHistoryResponse(Long id, Long actorUserId, String action, String oldValue,
                                   String newValue, String description, Instant timestamp) {
    public static AuditHistoryResponse from(AuditLog log) {
        return new AuditHistoryResponse(log.getId().longValue(),
                log.getActor() == null ? null : log.getActor().getId().longValue(),
                log.getAction(), log.getOldValue(), log.getNewValue(), log.getDescription(), log.getTimestamp());
    }
}
