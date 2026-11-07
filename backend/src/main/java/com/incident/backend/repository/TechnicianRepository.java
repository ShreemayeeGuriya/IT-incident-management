package com.incident.backend.repository;

import com.incident.backend.model.Role;
import com.incident.backend.model.AvailabilityStatus;
import com.incident.backend.model.Technician;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TechnicianRepository extends JpaRepository<Technician, Integer> {
    Optional<Technician> findByUserId(Integer userId);
    boolean existsByUserId(Integer userId);
    List<Technician> findAllByUserRoleOrderByIdAsc(Role role);
    List<Technician> findAllByUserRoleAndAvailabilityStatusNotOrderByIdAsc(
            Role role, AvailabilityStatus excludedAvailability);
}
