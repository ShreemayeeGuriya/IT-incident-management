package com.incident.backend.repository;

import com.incident.backend.model.Role;
import com.incident.backend.model.AvailabilityStatus;
import com.incident.backend.model.Technician;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface TechnicianRepository extends JpaRepository<Technician, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Technician t where t.id = :technicianId")
    Optional<Technician> findByIdForUpdate(@Param("technicianId") Integer technicianId);

    Optional<Technician> findByUserId(Integer userId);
    boolean existsByUserId(Integer userId);
    List<Technician> findAllByUserRoleOrderByIdAsc(Role role);
    List<Technician> findAllByUserRoleAndAvailabilityStatusNotOrderByIdAsc(
            Role role, AvailabilityStatus excludedAvailability);
}
