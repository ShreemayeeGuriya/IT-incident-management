package com.incident.backend.repository;

import com.incident.backend.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Integer> {
    List<AuditLog> findByIncidentIdOrderByTimestampAscIdAsc(Integer incidentId);
    long countByIncidentId(Integer incidentId);
}
