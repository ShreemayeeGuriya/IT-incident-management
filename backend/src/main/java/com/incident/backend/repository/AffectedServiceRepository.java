package com.incident.backend.repository;

import com.incident.backend.model.AffectedService;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AffectedServiceRepository extends JpaRepository<AffectedService, Integer> { }
