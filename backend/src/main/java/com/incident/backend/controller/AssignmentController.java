package com.incident.backend.controller;

import com.incident.backend.assignment.AssignmentService;
import com.incident.backend.dto.SkillsRequest;
import com.incident.backend.dto.StrategyResponse;
import com.incident.backend.dto.StrategySelectionRequest;
import com.incident.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/** Technician assignment endpoints (Stage 3). */
@RestController
@RequestMapping("/api")
public class AssignmentController {
    private final AssignmentService assignmentService;

    public AssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    /** Assign the incident with the active strategy. The incident must be PRIORITIZED. */
    @PostMapping("/incidents/{id}/auto-assign")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public AssignmentService.Result autoAssign(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        return assignmentService.autoAssign(id, p.getUser());
    }

    /** Who the active strategy would pick, without assigning. */
    @GetMapping("/incidents/{id}/assignment/recommendation")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public AssignmentService.Recommendation recommend(@PathVariable Long id) {
        return assignmentService.recommend(id);
    }

    @GetMapping("/assignment/strategies")
    @PreAuthorize("hasRole('ADMIN')")
    public StrategyResponse strategies() {
        return StrategyResponse.from(assignmentService.strategies());
    }

    @PutMapping("/assignment/strategy")
    @PreAuthorize("hasRole('ADMIN')")
    public StrategyResponse selectStrategy(@Valid @RequestBody StrategySelectionRequest request) {
        assignmentService.strategies().select(request.strategy());
        return StrategyResponse.from(assignmentService.strategies());
    }

    /** Open incidents, capacity and skills per technician. */
    @GetMapping("/technicians/workload")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public List<AssignmentService.TechnicianSummary> workload() {
        return assignmentService.workload();
    }

    @GetMapping("/skills")
    public List<String> skills() {
        return assignmentService.allSkills();
    }

    @GetMapping("/technicians/{id}/skills")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public Set<String> technicianSkills(@PathVariable Integer id) {
        return assignmentService.skillsOf(id);
    }

    @PutMapping("/technicians/{id}/skills")
    @PreAuthorize("hasRole('ADMIN')")
    public Set<String> replaceSkills(@PathVariable Integer id, @Valid @RequestBody SkillsRequest request) {
        return assignmentService.replaceSkills(id, request.skills());
    }
}
