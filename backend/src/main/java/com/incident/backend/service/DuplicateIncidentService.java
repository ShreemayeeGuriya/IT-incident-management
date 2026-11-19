package com.incident.backend.service;

import com.incident.backend.dto.DuplicateIncidentCheckRequest;
import com.incident.backend.dto.DuplicateIncidentSuggestion;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Incident;
import com.incident.backend.model.User;
import com.incident.backend.repository.AffectedServiceRepository;
import com.incident.backend.repository.CategoryRepository;
import com.incident.backend.repository.IncidentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/** Produces read-only duplicate suggestions before an incident is created. */
@Service
public class DuplicateIncidentService {
    private static final int MAX_CANDIDATES = 200;

    private final IncidentRepository incidentRepository;
    private final CategoryRepository categoryRepository;
    private final AffectedServiceRepository serviceRepository;
    private final DuplicateIncidentMatcher matcher;

    public DuplicateIncidentService(IncidentRepository incidentRepository,
                                    CategoryRepository categoryRepository,
                                    AffectedServiceRepository serviceRepository,
                                    DuplicateIncidentMatcher matcher) {
        this.incidentRepository = incidentRepository;
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.matcher = matcher;
    }

    @Transactional(readOnly = true)
    public List<DuplicateIncidentSuggestion> findSuggestions(DuplicateIncidentCheckRequest request, User current) {
        if (!categoryRepository.existsById(request.categoryId())) {
            throw new ResourceNotFoundException("Category not found: " + request.categoryId());
        }
        if (!serviceRepository.existsById(request.serviceId())) {
            throw new ResourceNotFoundException("Service not found: " + request.serviceId());
        }

        Integer reporterId = IncidentAccess.isStaff(current) ? null : current.getId();
        List<Incident> candidates = incidentRepository.findDuplicateCandidates(request.categoryId(),
                request.serviceId(), reporterId, PageRequest.of(0, MAX_CANDIDATES));

        return candidates.stream()
                .map(candidate -> new ScoredCandidate(candidate, matcher.score(request.description(),
                        request.categoryId(), request.serviceId(), candidate)))
                .filter(candidate -> candidate.score() > 0.0)
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.incident().getCreatedAt(), Comparator.reverseOrder())
                        .thenComparing(candidate -> candidate.incident().getId()))
                .map(candidate -> DuplicateIncidentSuggestion.from(candidate.incident(), candidate.score()))
                .toList();
    }

    private record ScoredCandidate(Incident incident, double score) { }
}
