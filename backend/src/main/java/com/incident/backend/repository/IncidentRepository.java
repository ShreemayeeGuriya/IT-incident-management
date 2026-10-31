package com.incident.backend.repository;

import com.incident.backend.model.Incident;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface IncidentRepository
        extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
}
