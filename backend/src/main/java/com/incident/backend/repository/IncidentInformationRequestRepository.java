package com.incident.backend.repository;

import com.incident.backend.model.IncidentInformationRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface IncidentInformationRequestRepository extends JpaRepository<IncidentInformationRequest, Integer> {
    List<IncidentInformationRequest> findByIncidentIdOrderByRequestedAtAscIdAsc(Integer incidentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IncidentInformationRequest r where r.id = :requestId and r.incident.id = :incidentId")
    Optional<IncidentInformationRequest> findByIdAndIncidentIdForUpdate(
            @Param("requestId") Integer requestId, @Param("incidentId") Integer incidentId);
}
