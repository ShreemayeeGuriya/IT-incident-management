package com.incident.backend.controller;

import com.incident.backend.dto.UpdateRoleRequest;
import com.incident.backend.dto.UserResponse;
import com.incident.backend.security.UserPrincipal;
import com.incident.backend.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/assignees")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public List<UserResponse> assignees() {
        return userService.assignees();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse updateRole(@PathVariable Long id,
                                   @Valid @RequestBody UpdateRoleRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return userService.updateRole(id, request.role(), principal.getUser());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        userService.delete(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
