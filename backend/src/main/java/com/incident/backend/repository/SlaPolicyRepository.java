package com.incident.backend.repository;

import com.incident.backend.model.SlaPolicy;
import com.incident.backend.model.Priority;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Integer> {
    Optional<SlaPolicy> findByPriority(Priority priority);
    boolean existsByPriority(Priority priority);
}
