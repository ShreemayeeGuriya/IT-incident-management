package com.incident.backend.repository;

import com.incident.backend.model.Incident;
import com.incident.backend.model.SlaObjectiveStatus;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository
        extends JpaRepository<Incident, Integer>, JpaSpecificationExecutor<Incident> {
    @Query("select count(i) > 0 from Incident i where i.assignedTechnician.id = :technicianId")
    boolean existsAssignedToTechnician(@Param("technicianId") Integer technicianId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.id = :incidentId")
    Optional<Incident> findByIdForUpdate(@Param("incidentId") Integer incidentId);

    @Query("""
            select i from Incident i
            join fetch i.category
            join fetch i.service
            where (:excludedIncidentId is null or i.id <> :excludedIncidentId)
              and (:reporterId is null or i.reporter.id = :reporterId)
            order by i.createdAt desc, i.id desc
            """)
    List<Incident> findDuplicateCandidates(@Param("reporterId") Integer reporterId,
                                          @Param("excludedIncidentId") Integer excludedIncidentId);

    @Query("""
            select i.id from Incident i
            where i.slaPolicy.id = :policyId and (
                (i.responseDeadline is not null and (
                    i.responseSlaStatus = :notStarted
                    or (i.responseSlaStatus = :within and i.responseDeadline <= :responseApproachCutoff)
                    or (i.responseSlaStatus = :approaching and i.responseDeadline <= :now)
                    or (i.responseSlaStatus in (:notStarted, :within, :approaching)
                        and (i.firstResponseAt is not null or i.resolvedAt is not null or i.closedAt is not null))
                ))
                or (i.resolutionDeadline is not null and (
                    i.resolutionSlaStatus = :notStarted
                    or (i.resolutionSlaStatus = :within and i.resolutionDeadline <= :resolutionApproachCutoff)
                    or (i.resolutionSlaStatus = :approaching and i.resolutionDeadline <= :now)
                    or (i.resolutionSlaStatus in (:notStarted, :within, :approaching)
                        and (i.resolvedAt is not null or i.closedAt is not null))
                ))
            )
            order by i.createdAt asc, i.id asc
            """)
    List<Integer> findSlaCandidateIds(@Param("policyId") Integer policyId,
                                     @Param("notStarted") SlaObjectiveStatus notStarted,
                                     @Param("within") SlaObjectiveStatus within,
                                     @Param("approaching") SlaObjectiveStatus approaching,
                                     @Param("responseApproachCutoff") Instant responseApproachCutoff,
                                     @Param("resolutionApproachCutoff") Instant resolutionApproachCutoff,
                                     @Param("now") Instant now,
                                     Pageable pageable);
}
