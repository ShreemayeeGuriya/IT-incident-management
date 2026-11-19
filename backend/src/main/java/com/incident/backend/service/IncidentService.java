package com.incident.backend.service;

import com.incident.backend.dto.*;
import com.incident.backend.events.IncidentCreatedEvent;
import com.incident.backend.events.IncidentUpdatedEvent;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ConflictException;
import com.incident.backend.exception.IncidentNotEligibleException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.*;
import com.incident.backend.priority.PriorityCalculator;
import com.incident.backend.priority.PriorityInput;
import com.incident.backend.priority.PriorityRecalculationPolicy;
import com.incident.backend.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class IncidentService {
    private static final Set<String> SORT_FIELDS = Set.of("createdAt", "priority", "status", "description", "id");

    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final AffectedServiceRepository serviceRepository;
    private final SlaService slaService;
    private final PriorityCalculator priorityCalculator;
    private final IncidentAccess access;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogRepository auditLogRepository;
    private final IncidentWorkflowService workflowService;

    public IncidentService(IncidentRepository incidentRepository, UserRepository userRepository,
                           CategoryRepository categoryRepository, AffectedServiceRepository serviceRepository,
                           SlaService slaService, PriorityCalculator priorityCalculator, IncidentAccess access,
                           ApplicationEventPublisher eventPublisher, AuditLogRepository auditLogRepository,
                           IncidentWorkflowService workflowService) {
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.slaService = slaService;
        this.priorityCalculator = priorityCalculator;
        this.access = access;
        this.eventPublisher = eventPublisher;
        this.auditLogRepository = auditLogRepository;
        this.workflowService = workflowService;
    }

    public IncidentResponse create(IncidentCreateRequest request, User current) {
        Category category = category(request.categoryId());
        AffectedService service = service(request.serviceId());
        Priority calculatedPriority = priorityCalculator.assess(new PriorityInput(
                request.impact(), request.urgency(), request.businessCriticality())).priority();
        if (request.priority() != null && request.priority() != calculatedPriority) {
            throw new BadRequestException("Supplied priority " + request.priority()
                    + " does not match calculated priority " + calculatedPriority);
        }
        SlaPolicy policy = slaService.resolvePolicy(calculatedPriority, request.slaPolicyId());

        Incident incident = new Incident();
        incident.setReporter(userRepository.getReferenceById(current.getId()));
        incident.setCategory(category);
        incident.setService(service);
        incident.setSlaPolicy(policy);
        incident.setDescription(request.description().trim());
        incident.setImpact(request.impact());
        incident.setUrgency(request.urgency());
        incident.setBusinessCriticality(request.businessCriticality());
        incident.setPriority(calculatedPriority);
        Incident saved = incidentRepository.saveAndFlush(incident);
        SlaService.Deadlines deadlines = slaService.calculateDeadlines(saved.getCreatedAt(), policy);
        saved.setResponseDeadline(deadlines.responseDeadline());
        saved.setResolutionDeadline(deadlines.resolutionDeadline());
        saved = incidentRepository.saveAndFlush(saved);
        Map<String, String> snapshotValues = new LinkedHashMap<>();
        snapshotValues.put("reporterId", current.getId().toString());
        snapshotValues.put("categoryId", category.getId().toString());
        snapshotValues.put("serviceId", service.getId().toString());
        snapshotValues.put("priority", saved.getPriority().name());
        snapshotValues.put("status", saved.getStatus().name());
        snapshotValues.put("impact", saved.getImpact().name());
        snapshotValues.put("urgency", saved.getUrgency().name());
        snapshotValues.put("businessCriticality", saved.getBusinessCriticality().name());
        if (policy != null) snapshotValues.put("slaPolicyId", policy.getId().toString());
        snapshotValues.put("responseDeadline", saved.getResponseDeadline().toString());
        snapshotValues.put("resolutionDeadline", saved.getResolutionDeadline().toString());
        eventPublisher.publishEvent(new IncidentCreatedEvent(UUID.randomUUID(), saved.getId(), current.getId(),
                Instant.now(), Map.of(), snapshotValues, "Incident created"));
        return workflowService.advanceCreationToPrioritized(saved.getId().longValue(), current);
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(Priority priority, IncidentStatus status, Long technicianId,
                                               String search, int page, int size, String sortBy,
                                               String direction, User current) {
        if (!SORT_FIELDS.contains(sortBy)) throw new BadRequestException("sortBy must be one of " + SORT_FIELDS);
        if (!direction.equalsIgnoreCase("asc") && !direction.equalsIgnoreCase("desc")) {
            throw new BadRequestException("direction must be asc or desc");
        }
        if (page < 0) throw new BadRequestException("page must be 0 or more");
        int pageSize = Math.min(Math.max(size, 1), 100);

        Specification<Incident> spec = (root, query, cb) -> cb.conjunction();
        if (!IncidentAccess.isStaff(current)) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("reporter").get("id"), current.getId()));
        }
        if (priority != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("priority"), priority));
        if (status != null) spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        if (technicianId != null) {
            int id = Math.toIntExact(technicianId);
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assignedTechnician").get("id"), id));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("description")), pattern));
        }

        Sort.Direction dir = direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(dir, sortBy).and(Sort.by(Sort.Direction.DESC, "id"));
        Page<Incident> result = incidentRepository.findAll(spec, PageRequest.of(page, pageSize, sort));
        return PageResponse.from(result.map(IncidentResponse::from));
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(Long id, User current) {
        return IncidentResponse.from(access.requireViewable(id, current));
    }

    public IncidentResponse update(Long id, IncidentUpdateRequest request, User current) {
        Incident incident = access.requireViewableForUpdate(id, current);
        Map<String, String> oldValues = new LinkedHashMap<>();
        Map<String, String> newValues = new LinkedHashMap<>();
        Set<String> changedFields = new java.util.LinkedHashSet<>();
        Priority originalPriority = incident.getPriority();
        SlaPolicy originalPolicy = incident.getSlaPolicy();
        Instant originalResponseDeadline = incident.getResponseDeadline();
        Instant originalResolutionDeadline = incident.getResolutionDeadline();
        boolean staff = IncidentAccess.isStaff(current);
        if (!staff && hasStaffFields(request)) {
            throw new AccessDeniedException("Only a technician or admin can change incident classification fields");
        }

        Impact nextImpact = request.impact() == null ? incident.getImpact() : request.impact();
        Urgency nextUrgency = request.urgency() == null ? incident.getUrgency() : request.urgency();
        BusinessCriticality nextCriticality = request.businessCriticality() == null
                ? incident.getBusinessCriticality() : request.businessCriticality();
        boolean factorsChanged = nextImpact != incident.getImpact()
                || nextUrgency != incident.getUrgency()
                || nextCriticality != incident.getBusinessCriticality();
        if (factorsChanged && PriorityRecalculationPolicy.isLocked(incident.getStatus())) {
            throw new IncidentNotEligibleException("change classification factors", incident.getStatus(),
                    "impact, urgency and business criticality are fixed once an incident is resolved");
        }

        Priority calculatedPriority = incident.getPriority();
        if (factorsChanged || request.priority() != null) {
            calculatedPriority = priorityCalculator.assess(
                    new PriorityInput(nextImpact, nextUrgency, nextCriticality)).priority();
            if (request.priority() != null && request.priority() != calculatedPriority) {
                throw new BadRequestException("Supplied priority " + request.priority()
                        + " does not match calculated priority " + calculatedPriority);
            }
        }
        boolean priorityChanged = originalPriority != calculatedPriority;

        // Resolve the resulting policy and deadlines before mutating the managed entity. This keeps
        // validation failures from leaving an incident partially changed within the transaction.
        SlaPolicy effectivePolicy = originalPolicy;
        if (request.slaPolicyId() != null) {
            effectivePolicy = slaService.resolvePolicy(calculatedPriority, request.slaPolicyId());
        } else if (priorityChanged) {
            effectivePolicy = slaService.resolvePolicy(calculatedPriority, null);
        }
        boolean policyChanged = !java.util.Objects.equals(
                originalPolicy == null ? null : originalPolicy.getId(),
                effectivePolicy == null ? null : effectivePolicy.getId());
        boolean recalculateDeadlines = priorityChanged || policyChanged;
        SlaService.Deadlines deadlines = recalculateDeadlines
                ? slaService.calculateDeadlines(incident.getCreatedAt(), effectivePolicy) : null;

        if (request.description() != null) {
            if (request.description().isBlank()) throw new BadRequestException("Description cannot be blank");
            String next = request.description().trim();
            trackRedactedText(oldValues, newValues, changedFields, "description",
                    incident.getDescription(), next);
            incident.setDescription(next);
        }
        if (request.categoryId() != null) {
            Category next = category(request.categoryId());
            track(oldValues, newValues, changedFields, "categoryId", incident.getCategory().getId(), next.getId());
            incident.setCategory(next);
        }
        if (request.serviceId() != null) {
            AffectedService next = service(request.serviceId());
            track(oldValues, newValues, changedFields, "serviceId", incident.getService().getId(), next.getId());
            incident.setService(next);
        }
        track(oldValues, newValues, changedFields, "impact", incident.getImpact(), nextImpact);
        incident.setImpact(nextImpact);
        track(oldValues, newValues, changedFields, "urgency", incident.getUrgency(), nextUrgency);
        incident.setUrgency(nextUrgency);
        track(oldValues, newValues, changedFields, "businessCriticality",
                incident.getBusinessCriticality(), nextCriticality);
        incident.setBusinessCriticality(nextCriticality);
        track(oldValues, newValues, changedFields, "priority", originalPriority, calculatedPriority);
        incident.setPriority(calculatedPriority);
        if (request.investigationDetails() != null) {
            trackRedactedText(oldValues, newValues, changedFields, "investigationDetails",
                    incident.getInvestigationDetails(), request.investigationDetails());
            incident.setInvestigationDetails(request.investigationDetails());
        }
        if (request.resolutionDetails() != null) {
            trackRedactedText(oldValues, newValues, changedFields, "resolutionDetails",
                    incident.getResolutionDetails(), request.resolutionDetails());
            incident.setResolutionDetails(request.resolutionDetails());
        }
        if (request.closureDetails() != null) {
            trackRedactedText(oldValues, newValues, changedFields, "closureDetails",
                    incident.getClosureDetails(), request.closureDetails());
            incident.setClosureDetails(request.closureDetails());
        }

        if (request.slaPolicyId() != null || priorityChanged) {
            incident.setSlaPolicy(effectivePolicy);
        }
        if (policyChanged) {
            track(oldValues, newValues, changedFields, "slaPolicyId",
                    originalPolicy == null ? null : originalPolicy.getId(),
                    effectivePolicy == null ? null : effectivePolicy.getId());
        }

        if (recalculateDeadlines) {
            incident.setResponseDeadline(deadlines.responseDeadline());
            incident.setResolutionDeadline(deadlines.resolutionDeadline());
            track(oldValues, newValues, changedFields, "responseDeadline",
                    originalResponseDeadline, deadlines.responseDeadline());
            track(oldValues, newValues, changedFields, "resolutionDeadline",
                    originalResolutionDeadline, deadlines.resolutionDeadline());
        }

        Incident saved = incidentRepository.saveAndFlush(incident);
        if (!changedFields.isEmpty()) {
            eventPublisher.publishEvent(new IncidentUpdatedEvent(UUID.randomUUID(), saved.getId(), current.getId(),
                    Instant.now(), oldValues, newValues, "Changed fields: " + String.join(", ", changedFields)));
        }
        return IncidentResponse.from(saved);
    }

    private void track(Map<String, String> oldValues, Map<String, String> newValues, Set<String> fields,
                       String field, Object oldValue, Object newValue) {
        if (!java.util.Objects.equals(oldValue, newValue)) {
            fields.add(field);
            if (oldValue != null) oldValues.put(field, oldValue.toString());
            if (newValue != null) newValues.put(field, newValue.toString());
        }
    }

    private void trackRedactedText(Map<String, String> oldValues, Map<String, String> newValues,
                                   Set<String> fields, String field, String oldValue, String newValue) {
        if (!java.util.Objects.equals(oldValue, newValue)) {
            fields.add(field);
            oldValues.put(field, isEmptyText(oldValue) ? "EMPTY" : "PRESENT");
            newValues.put(field, isEmptyText(newValue) ? "EMPTY" : "PRESENT");
        }
    }

    private boolean isEmptyText(String value) {
        return value == null || value.isBlank();
    }

    public void delete(Long id) {
        Incident incident = access.findOrThrow(id);
        if (auditLogRepository.countByIncidentId(incident.getId()) > 0) {
            throw new ConflictException("Incident cannot be deleted because it has audit history");
        }
        incidentRepository.delete(incident);
    }

    private boolean hasStaffFields(IncidentUpdateRequest r) {
        return r.categoryId() != null || r.serviceId() != null || r.slaPolicyId() != null
                || r.impact() != null || r.urgency() != null || r.businessCriticality() != null
                || r.priority() != null || r.investigationDetails() != null
                || r.resolutionDetails() != null || r.closureDetails() != null;
    }

    private Category category(Integer id) {
        return categoryRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Category not found: " + id));
    }

    private AffectedService service(Integer id) {
        return serviceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
    }

}
