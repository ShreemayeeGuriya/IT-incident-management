package com.incident.backend.controller;

import com.incident.backend.dto.AssignRequest;
import com.incident.backend.dto.CommentRequest;
import com.incident.backend.dto.CommentResponse;
import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.dto.PageResponse;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.model.IncidentState;
import com.incident.backend.model.Priority;
import com.incident.backend.security.UserPrincipal;
import com.incident.backend.service.CommentService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.IncidentWorkflowService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final IncidentWorkflowService workflowService;
    private final CommentService commentService;

    public IncidentController(IncidentService incidentService,
                              IncidentWorkflowService workflowService,
                              CommentService commentService) {
        this.incidentService = incidentService;
        this.workflowService = workflowService;
        this.commentService = commentService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody IncidentCreateRequest request,
                                                   @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.create(request, p.getUser()));
    }

    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) IncidentState state,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @AuthenticationPrincipal UserPrincipal p) {
        return incidentService.list(priority, state, assigneeId, search, page, size,
                sortBy, direction, p.getUser());
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        return incidentService.get(id, p.getUser());
    }

    @PutMapping("/{id}")
    public IncidentResponse update(@PathVariable Long id,
                                   @Valid @RequestBody IncidentUpdateRequest request,
                                   @AuthenticationPrincipal UserPrincipal p) {
        return incidentService.update(id, request, p.getUser());
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public IncidentResponse assign(@PathVariable Long id, @RequestBody AssignRequest request) {
        return workflowService.assign(id, request.assigneeId());
    }

    @PatchMapping("/{id}/state")
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public IncidentResponse changeState(@PathVariable Long id,
                                        @Valid @RequestBody StateChangeRequest request) {
        return workflowService.changeState(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        incidentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comments")
    public List<CommentResponse> comments(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal p) {
        return commentService.list(id, p.getUser());
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
                                                      @Valid @RequestBody CommentRequest request,
                                                      @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.add(id, request.text(), p.getUser()));
    }
}
