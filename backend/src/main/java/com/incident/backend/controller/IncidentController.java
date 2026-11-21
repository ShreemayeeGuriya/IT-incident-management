package com.incident.backend.controller;

import com.incident.backend.dto.AssignRequest;
import com.incident.backend.dto.AuditHistoryResponse;
import com.incident.backend.dto.CommentRequest;
import com.incident.backend.dto.CommentResponse;
import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.dto.InformationRequestCreateRequest;
import com.incident.backend.dto.InformationResponseCreateRequest;
import com.incident.backend.dto.IncidentInformationRequestResponse;
import com.incident.backend.dto.DuplicateIncidentCheckRequest;
import com.incident.backend.dto.DuplicateIncidentSuggestion;
import com.incident.backend.dto.PageResponse;
import com.incident.backend.dto.StateChangeRequest;
import com.incident.backend.model.IncidentStatus;
import com.incident.backend.model.Priority;
import com.incident.backend.security.UserPrincipal;
import com.incident.backend.service.CommentService;
import com.incident.backend.service.AuditLogService;
import com.incident.backend.service.IncidentService;
import com.incident.backend.service.IncidentWorkflowService;
//import com.incident.backend.service.AdditionalInformationService;
import com.incident.backend.service.DuplicateIncidentService;
import com.incident.backend.service.IncidentInformationRequestService;
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
    private final AuditLogService auditLogService;
    private final DuplicateIncidentService duplicateIncidentService;
private final IncidentInformationRequestService informationRequestService;

    public IncidentController(IncidentService incidentService,
                              IncidentWorkflowService workflowService,
                              CommentService commentService, AuditLogService auditLogService,
                              DuplicateIncidentService duplicateIncidentService,
IncidentInformationRequestService informationRequestService) {
        this.incidentService = incidentService;
        this.workflowService = workflowService;
        this.commentService = commentService;
        this.auditLogService = auditLogService;
        this.duplicateIncidentService = duplicateIncidentService;
this.informationRequestService = informationRequestService;
    }

    @PostMapping
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody IncidentCreateRequest request,
                                                   @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.status(HttpStatus.CREATED).body(incidentService.create(request, p.getUser()));
    }

    @PostMapping("/duplicate-suggestions")
    public List<DuplicateIncidentSuggestion> duplicateSuggestions(
            @Valid @RequestBody DuplicateIncidentCheckRequest request,
            @AuthenticationPrincipal UserPrincipal p) {
        return duplicateIncidentService.findSuggestions(request, p.getUser());
    }

    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @AuthenticationPrincipal UserPrincipal p) {
        Long assignmentFilter = technicianId != null ? technicianId : assigneeId;
        return incidentService.list(priority, status, categoryId, assignmentFilter, search, page, size,
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
    public IncidentResponse assign(@PathVariable Long id, @RequestBody AssignRequest request,
                                   @AuthenticationPrincipal UserPrincipal p) {
        return workflowService.assign(id, request.technicianId(), p.getUser());
    }

    @PatchMapping({"/{id}/status", "/{id}/state"})
    @PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
    public IncidentResponse changeState(@PathVariable Long id,
                                        @Valid @RequestBody StateChangeRequest request,
                                        @AuthenticationPrincipal UserPrincipal p) {
        return workflowService.changeState(id, request, p.getUser());
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

    @GetMapping("/{id}/audit")
    public List<AuditHistoryResponse> auditHistory(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserPrincipal p) {
        return auditLogService.history(id, p.getUser()).stream().map(AuditHistoryResponse::from).toList();
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<CommentResponse> addComment(@PathVariable Long id,
                                                      @Valid @RequestBody CommentRequest request,
                                                      @AuthenticationPrincipal UserPrincipal p) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.add(id, request.text(), p.getUser()));
    }

@GetMapping("/{id}/information-requests")
public List<IncidentInformationRequestResponse> informationRequests(
        @PathVariable Long id,
        @AuthenticationPrincipal UserPrincipal p) {
    return informationRequestService.list(id, p.getUser());
}

@PostMapping("/{id}/information-requests")
@PreAuthorize("hasRole('TECHNICIAN')")
public ResponseEntity<IncidentInformationRequestResponse> requestInformation(
        @PathVariable Long id,
        @Valid @RequestBody InformationRequestCreateRequest request,
        @AuthenticationPrincipal UserPrincipal p) {
    return ResponseEntity.status(HttpStatus.CREATED).body(
            informationRequestService.request(id, request.message(), p.getUser()));
}

@PostMapping("/{id}/information-requests/{requestId}/response")
@PreAuthorize("hasRole('EMPLOYEE')")
public IncidentInformationRequestResponse respondToInformationRequest(
        @PathVariable Long id,
        @PathVariable Long requestId,
        @Valid @RequestBody InformationResponseCreateRequest request,
        @AuthenticationPrincipal UserPrincipal p) {
    return informationRequestService.respond(id, requestId, request.message(), p.getUser());
}
    }

