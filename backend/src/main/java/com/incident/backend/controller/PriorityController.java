package com.incident.backend.controller;

import com.incident.backend.dto.PriorityPreviewRequest;
import com.incident.backend.dto.PriorityRecalculationResponse;
import com.incident.backend.dto.StrategyResponse;
import com.incident.backend.dto.StrategySelectionRequest;
import com.incident.backend.priority.PriorityAssessment;
import com.incident.backend.priority.PriorityInput;
import com.incident.backend.priority.PriorityService;
import com.incident.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Priority calculation endpoints (Stage 2). */
@RestController
@RequestMapping("/api")
public class PriorityController {
    private final PriorityService priorityService;

    public PriorityController(PriorityService priorityService) {
        this.priorityService = priorityService;
    }

    /** Any signed-in user: preview the priority for a set of factors, e.g. on the report form. */
    @PostMapping("/priority/preview")
    public PriorityAssessment preview(@Valid @RequestBody PriorityPreviewRequest request) {
        return priorityService.assess(new PriorityInput(request.impact(), request.urgency(),
                request.businessCriticality()));
    }

    /** What the incident's priority should be, without changing it. */
    @GetMapping("/incidents/{id}/priority/assessment")
    public PriorityAssessment assessment(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        return priorityService.assess(id, p.getUser());
    }

    /** Recalculate and save the incident's priority (SLA deadlines follow automatically). */
    @PostMapping("/incidents/{id}/priority/recalculate")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public PriorityRecalculationResponse recalculate(@PathVariable Long id,
                                                     @AuthenticationPrincipal UserPrincipal p) {
        return PriorityRecalculationResponse.from(priorityService.recalculate(id, p.getUser()));
    }

    @GetMapping("/priority/strategies")
    @PreAuthorize("hasRole('ADMIN')")
    public StrategyResponse strategies() {
        return StrategyResponse.from(priorityService.strategies());
    }

    @PutMapping("/priority/strategy")
    @PreAuthorize("hasRole('ADMIN')")
    public StrategyResponse selectStrategy(@Valid @RequestBody StrategySelectionRequest request) {
        priorityService.strategies().select(request.strategy());
        return StrategyResponse.from(priorityService.strategies());
    }
}
