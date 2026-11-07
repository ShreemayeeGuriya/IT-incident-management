package com.incident.backend.controller;

import com.incident.backend.dto.CategoryResponse;
import com.incident.backend.dto.ServiceResponse;
import com.incident.backend.dto.SlaPolicyResponse;
import com.incident.backend.repository.AffectedServiceRepository;
import com.incident.backend.repository.CategoryRepository;
import com.incident.backend.repository.SlaPolicyRepository;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class LookupController {
    private final CategoryRepository categories;
    private final AffectedServiceRepository services;
    private final SlaPolicyRepository policies;

    public LookupController(CategoryRepository categories, AffectedServiceRepository services,
                            SlaPolicyRepository policies) {
        this.categories = categories;
        this.services = services;
        this.policies = policies;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return categories.findAll(Sort.by("id")).stream().map(CategoryResponse::from).toList();
    }

    @GetMapping("/services")
    public List<ServiceResponse> services() {
        return services.findAll(Sort.by("id")).stream().map(ServiceResponse::from).toList();
    }

    @GetMapping("/sla-policies")
    public List<SlaPolicyResponse> policies() {
        return policies.findAll(Sort.by("id")).stream().map(SlaPolicyResponse::from).toList();
    }
}
