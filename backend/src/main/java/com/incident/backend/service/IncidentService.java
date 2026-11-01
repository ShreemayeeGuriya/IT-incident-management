package com.incident.backend.service;

import com.incident.backend.dto.IncidentCreateRequest;
import com.incident.backend.dto.IncidentResponse;
import com.incident.backend.dto.IncidentUpdateRequest;
import com.incident.backend.dto.PageResponse;
import com.incident.backend.exception.BadRequestException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.IncidentState;
import com.incident.backend.model.Priority;
import com.incident.backend.model.User;
import com.incident.backend.repository.IncidentRepository;
import com.incident.backend.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@Transactional
public class IncidentService {

    private static final Set<String> SORT_FIELDS =
            Set.of("updatedAt", "createdAt", "priority", "state", "title", "id");

    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final IncidentAccess access;

    public IncidentService(IncidentRepository incidentRepository, UserRepository userRepository,
                           IncidentAccess access) {
        this.incidentRepository = incidentRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    public IncidentResponse create(IncidentCreateRequest r, User current) {
        Incident i = new Incident();
        i.setTitle(r.title().trim());
        i.setDescription(r.description().trim());
        i.setComponent(r.component());
        i.setClassification(r.classification());
        i.setReproducibility(r.reproducibility());
        i.setMilestone(r.milestone());
        i.setCategory(r.category());
        i.setKeywords(r.keywords());
        i.setRequestor(userRepository.getReferenceById(current.getId()));
        return IncidentResponse.from(incidentRepository.save(i));
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(Priority priority, IncidentState state, Long assigneeId,
                                               String search, int page, int size,
                                               String sortBy, String direction, User current) {
        if (!SORT_FIELDS.contains(sortBy)) {
            throw new BadRequestException("sortBy must be one of " + SORT_FIELDS);
        }
        if (!direction.equalsIgnoreCase("asc") && !direction.equalsIgnoreCase("desc")) {
            throw new BadRequestException("direction must be asc or desc");
        }
        if (page < 0) {
            throw new BadRequestException("page must be 0 or more");
        }
        int pageSize = Math.min(Math.max(size, 1), 100);

        Specification<Incident> spec = (root, query, cb) -> cb.conjunction();
        if (!IncidentAccess.isStaff(current)) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("requestor").get("id"), current.getId()));
        }
        if (priority != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("priority"), priority));
        }
        if (state != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("state"), state));
        }
        if (assigneeId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("assignee").get("id"), assigneeId));
        }
        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.or(
                    cb.like(cb.lower(root.get("title")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)));
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

    public IncidentResponse update(Long id, IncidentUpdateRequest r, User current) {
        Incident i = access.requireViewable(id, current);
        boolean staff = IncidentAccess.isStaff(current);
        if (!staff && (r.classification() != null || r.priority() != null || r.milestone() != null
                || r.category() != null || r.keywords() != null || r.rootCause() != null)) {
            throw new AccessDeniedException("Only a technician or admin can change classification, "
                    + "priority, milestone, category, keywords or root cause");
        }
        if (r.title() != null) {
            if (r.title().isBlank()) {
                throw new BadRequestException("Title cannot be blank");
            }
            i.setTitle(r.title().trim());
        }
        if (r.description() != null) {
            if (r.description().isBlank()) {
                throw new BadRequestException("Description cannot be blank");
            }
            i.setDescription(r.description().trim());
        }
        if (r.component() != null) i.setComponent(r.component());
        if (r.reproducibility() != null) i.setReproducibility(r.reproducibility());
        if (staff) {
            if (r.classification() != null) i.setClassification(r.classification());
            if (r.priority() != null) i.setPriority(r.priority());
            if (r.milestone() != null) i.setMilestone(r.milestone());
            if (r.category() != null) i.setCategory(r.category());
            if (r.keywords() != null) i.setKeywords(r.keywords());
            if (r.rootCause() != null) i.setRootCause(r.rootCause());
        }
        return IncidentResponse.from(incidentRepository.saveAndFlush(i));
    }

    public void delete(Long id) {
        incidentRepository.delete(access.findOrThrow(id));
    }
}
