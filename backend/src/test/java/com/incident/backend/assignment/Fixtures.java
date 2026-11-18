package com.incident.backend.assignment;

import com.incident.backend.model.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

/** Builds in-memory entities for strategy and rule tests (no database). */
final class Fixtures {
    private Fixtures() { }

    static TechnicianCandidate candidate(int id, long active, int max, String... skills) {
        return candidate(id, active, max, AvailabilityStatus.AVAILABLE, Role.TECHNICIAN, skills);
    }

    static TechnicianCandidate candidate(int id, long active, int max, AvailabilityStatus availability, Role role,
                                         String... skills) {
        User user = new User();
        user.setName("Tech " + id);
        user.setRole(role);
        Technician technician = new Technician(user);
        ReflectionTestUtils.setField(technician, "id", id);
        ReflectionTestUtils.setField(technician, "maxWorkload", max);
        ReflectionTestUtils.setField(technician, "availabilityStatus", availability);
        return new TechnicianCandidate(technician, active, Set.of(skills));
    }

    static Incident incident(String categoryName, IncidentStatus status) {
        return incident(category(categoryName), status);
    }

    static Incident incident(Category category, IncidentStatus status) {
        Incident incident = new Incident();
        incident.setCategory(category);
        incident.setStatus(status);
        return incident;
    }

    static Category category(String name) {
        try {
            var ctor = Category.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            Category category = ctor.newInstance();
            ReflectionTestUtils.setField(category, "name", name);
            return category;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
