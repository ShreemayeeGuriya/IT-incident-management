package com.incident.backend.service;

import com.incident.backend.dto.UserResponse;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ConflictException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Role;
import com.incident.backend.model.User;
import com.incident.backend.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    public List<UserResponse> assignees() {
        return userRepository.findByRoleIn(List.of(Role.TECHNICIAN, Role.ADMIN))
                .stream().map(UserResponse::from).toList();
    }

    public UserResponse updateRole(Long id, Role newRole, User current) {
        User target = find(id);
        if (target.getId().equals(current.getId()) && newRole != Role.ADMIN) {
            throw new BadRequestException("You cannot remove your own admin role");
        }
        target.setRole(newRole);
        return UserResponse.from(userRepository.save(target));
    }

    public void delete(Long id, User current) {
        User target = find(id);
        if (target.getId().equals(current.getId())) {
            throw new BadRequestException("You cannot delete your own account");
        }
        try {
            userRepository.delete(target);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This user has incidents or comments and cannot be deleted");
        }
    }

    private User find(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }
}
