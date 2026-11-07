package com.incident.backend.service;

import com.incident.backend.dto.UserResponse;
import com.incident.backend.dto.TechnicianResponse;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ConflictException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Role;
import com.incident.backend.model.User;
import com.incident.backend.repository.UserRepository;
import com.incident.backend.repository.TechnicianRepository;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.model.Technician;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TechnicianRepository technicianRepository;
    private final IncidentRepository incidentRepository;

    public UserService(UserRepository userRepository, TechnicianRepository technicianRepository,
                       IncidentRepository incidentRepository) {
        this.userRepository = userRepository;
        this.technicianRepository = technicianRepository;
        this.incidentRepository = incidentRepository;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public List<TechnicianResponse> assignees() {
        return technicianRepository.findAllByUserRoleOrderByIdAsc(Role.TECHNICIAN)
                .stream().map(TechnicianResponse::from).toList();
    }

    @Transactional
    public UserResponse updateRole(Long id, Role newRole, User current) {
        User target = find(id);
        if (target.getId().equals(current.getId()) && newRole != Role.ADMIN) {
            throw new BadRequestException("You cannot remove your own admin role");
        }
        Role previousRole = target.getRole();
        if (previousRole == Role.TECHNICIAN && newRole != Role.TECHNICIAN) {
            technicianRepository.findByUserId(target.getId()).ifPresent(technician -> {
                if (incidentRepository.existsAssignedToTechnician(technician.getId())) {
                    throw new ConflictException("Reassign incidents before removing this technician role");
                }
                technicianRepository.delete(technician);
                technicianRepository.flush();
            });
        }
        target.setRole(newRole);
        User saved = userRepository.save(target);
        if (newRole == Role.TECHNICIAN && !technicianRepository.existsByUserId(saved.getId())) {
            technicianRepository.save(new Technician(saved));
        }
        return UserResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, User current) {
        User target = find(id);
        if (target.getId().equals(current.getId())) {
            throw new BadRequestException("You cannot delete your own account");
        }
        try {
            technicianRepository.findByUserId(target.getId()).ifPresent(technician -> {
                if (incidentRepository.existsAssignedToTechnician(technician.getId())) {
                    throw new ConflictException("Reassign incidents before deleting this technician");
                }
                technicianRepository.delete(technician);
                technicianRepository.flush();
            });
            userRepository.delete(target);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This user has incidents or comments and cannot be deleted");
        }
    }

    private User find(Long id) {
        return userRepository.findById(Math.toIntExact(id))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
