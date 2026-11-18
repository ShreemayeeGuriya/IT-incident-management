package com.incident.backend.repository;

import com.incident.backend.model.IncidentEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IncidentEscalationRepository extends JpaRepository<IncidentEscalation, Integer> {
    List<IncidentEscalation> findByIncidentIdOrderByEscalationLevelAsc(Integer incidentId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO incident_escalations
                (incident_id, escalation_level, trigger_objective, created_at)
            VALUES (:incidentId, :level, :objective, :createdAt)
            ON CONFLICT (incident_id, escalation_level) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("incidentId") Integer incidentId,
                       @Param("level") Integer level,
                       @Param("objective") String objective,
                       @Param("createdAt") Instant createdAt);
}
