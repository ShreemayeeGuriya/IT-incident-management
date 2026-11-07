package com.incident.backend.service;

import com.incident.backend.exception.BadRequestException;
import com.incident.backend.exception.ConflictException;
import com.incident.backend.exception.ResourceNotFoundException;
import com.incident.backend.model.Priority;
import com.incident.backend.model.SlaPolicy;
import com.incident.backend.model.SlaObjectiveStatus;
import com.incident.backend.model.SlaStatus;
import com.incident.backend.repository.SlaPolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlaServiceTest {
    @Mock SlaPolicyRepository slaPolicyRepository;
    @InjectMocks SlaService slaService;

    @Test
    void automaticallySelectsPolicyByPriority() {
        SlaPolicy policy = policy(1, Priority.P1, 5, 30);
        when(slaPolicyRepository.findByPriority(Priority.P1)).thenReturn(Optional.of(policy));

        assertSame(policy, slaService.resolvePolicy(Priority.P1, null));
    }

    @Test
    void explicitMatchingPolicyTakesPrecedenceOverPriorityLookup() {
        SlaPolicy explicit = policy(10, Priority.P2, 20, 90);
        when(slaPolicyRepository.findById(10)).thenReturn(Optional.of(explicit));

        assertSame(explicit, slaService.resolvePolicy(Priority.P2, 10));
        verify(slaPolicyRepository, never()).findByPriority(any());
    }

    @Test
    void rejectsExplicitPolicyForDifferentPriority() {
        SlaPolicy p1 = policy(1, Priority.P1, 5, 30);
        when(slaPolicyRepository.findById(1)).thenReturn(Optional.of(p1));

        assertEquals("SLA policy priority must match incident priority",
                assertThrows(BadRequestException.class,
                        () -> slaService.resolvePolicy(Priority.P2, 1)).getMessage());
    }

    @Test
    void reportsMissingExplicitPolicy() {
        when(slaPolicyRepository.findById(999)).thenReturn(Optional.empty());

        assertEquals("SLA policy not found: 999",
                assertThrows(ResourceNotFoundException.class,
                        () -> slaService.resolvePolicy(Priority.P1, 999)).getMessage());
    }

    @Test
    void reportsMissingPolicyForRequiredPriority() {
        when(slaPolicyRepository.findByPriority(Priority.P4)).thenReturn(Optional.empty());

        assertEquals("No SLA policy is configured for priority P4",
                assertThrows(ConflictException.class,
                        () -> slaService.resolvePolicy(Priority.P4, null)).getMessage());
    }

    @Test
    void calculatesBothDeadlinesFromOriginalCreationTime() {
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
        SlaPolicy policy = policy(1, Priority.P1, 5, 30);

        SlaService.Deadlines deadlines = slaService.calculateDeadlines(createdAt, policy);

        assertEquals(Instant.parse("2026-01-02T03:09:05Z"), deadlines.responseDeadline());
        assertEquals(Instant.parse("2026-01-02T03:34:05Z"), deadlines.resolutionDeadline());
    }

    @Test
    void responseObjectiveUsesConfiguredThresholdAndDeadlineBoundary() {
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
        Instant deadline = createdAt.plus(Duration.ofMinutes(100));

        assertEquals(SlaObjectiveStatus.WITHIN_SLA, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.NOT_STARTED, createdAt, deadline, null, null, null,
                createdAt.plus(Duration.ofMinutes(79)), 0.8));
        assertEquals(SlaObjectiveStatus.APPROACHING_BREACH, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.WITHIN_SLA, createdAt, deadline, null, null, null,
                createdAt.plus(Duration.ofMinutes(80)), 0.8));
        assertEquals(SlaObjectiveStatus.BREACHED, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.APPROACHING_BREACH, createdAt, deadline, null, null, null,
                deadline, 0.8));
    }

    @Test
    void responseCompletionUsesItsActualTimestampAndRemainsTerminal() {
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
        Instant deadline = createdAt.plus(Duration.ofMinutes(5));
        Instant monitorTime = deadline.plus(Duration.ofHours(1));

        assertEquals(SlaObjectiveStatus.MET, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.APPROACHING_BREACH, createdAt, deadline,
                deadline, null, null, monitorTime, 0.8));
        assertEquals(SlaObjectiveStatus.BREACHED, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.APPROACHING_BREACH, createdAt, deadline,
                deadline.plusNanos(1), null, null, monitorTime, 0.8));
        assertEquals(SlaObjectiveStatus.BREACHED, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.BREACHED, createdAt, deadline,
                createdAt, null, null, createdAt, 0.8));
        assertEquals(SlaObjectiveStatus.MET, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.MET, createdAt, deadline,
                deadline.plus(Duration.ofHours(1)), null, null, monitorTime, 0.8));
    }

    @Test
    void responseUsesResolvedThenClosedAsFallbackWithoutOverridingFirstResponse() {
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
        Instant deadline = createdAt.plus(Duration.ofMinutes(5));
        Instant resolvedOnTime = deadline.minusNanos(1);
        Instant closedLate = deadline.plus(Duration.ofHours(1));

        assertEquals(SlaObjectiveStatus.MET, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.NOT_STARTED, createdAt, deadline, null,
                resolvedOnTime, closedLate, closedLate, 0.8));
        assertEquals(SlaObjectiveStatus.MET, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.NOT_STARTED, createdAt, deadline, null,
                null, deadline, closedLate, 0.8));
        assertEquals(SlaObjectiveStatus.BREACHED, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.NOT_STARTED, createdAt, deadline, deadline.plusNanos(1),
                resolvedOnTime, closedLate, closedLate, 0.8));
    }

    @Test
    void resolutionUsesResolvedTimestampBeforeLaterClosureAndClosedFallback() {
        Instant createdAt = Instant.parse("2026-01-02T03:04:05Z");
        Instant deadline = createdAt.plus(Duration.ofMinutes(30));
        Instant resolvedOnTime = deadline.minusNanos(1);
        Instant closedLate = deadline.plus(Duration.ofHours(1));

        assertEquals(SlaObjectiveStatus.MET, slaService.evaluateResolutionStatus(
                SlaObjectiveStatus.NOT_STARTED, createdAt, deadline,
                resolvedOnTime, closedLate, closedLate, 0.8));
        assertEquals(SlaObjectiveStatus.BREACHED, slaService.evaluateResolutionStatus(
                SlaObjectiveStatus.APPROACHING_BREACH, createdAt, deadline,
                null, closedLate, closedLate, 0.8));
    }

    @Test
    void objectivesWithoutDeadlineStayNotStartedAndCompatibilityStatusRollsUpIndependently() {
        Instant now = Instant.parse("2026-01-02T03:04:05Z");
        assertEquals(SlaObjectiveStatus.NOT_STARTED, slaService.evaluateResponseStatus(
                SlaObjectiveStatus.NOT_STARTED, now, null, null, null, null, now, 0.8));
        assertEquals(SlaStatus.RESPONSE_BREACHED, slaService.compatibilityStatus(
                SlaObjectiveStatus.BREACHED, SlaObjectiveStatus.MET));
        assertEquals(SlaStatus.RESOLUTION_BREACHED, slaService.compatibilityStatus(
                SlaObjectiveStatus.MET, SlaObjectiveStatus.BREACHED));
        assertEquals(SlaStatus.BREACHED, slaService.compatibilityStatus(
                SlaObjectiveStatus.BREACHED, SlaObjectiveStatus.BREACHED));
        assertEquals(SlaStatus.MET, slaService.compatibilityStatus(
                SlaObjectiveStatus.MET, SlaObjectiveStatus.MET));
        assertEquals(SlaStatus.NOT_STARTED, slaService.compatibilityStatus(
                SlaObjectiveStatus.NOT_STARTED, SlaObjectiveStatus.NOT_STARTED));
        assertEquals(SlaStatus.WITHIN_SLA, slaService.compatibilityStatus(
                SlaObjectiveStatus.MET, SlaObjectiveStatus.APPROACHING_BREACH));
    }

    private SlaPolicy policy(int id, Priority priority, int responseMinutes, int resolutionMinutes) {
        SlaPolicy policy = mock(SlaPolicy.class);
        lenient().when(policy.getId()).thenReturn(id);
        lenient().when(policy.getPriority()).thenReturn(priority);
        lenient().when(policy.getResponseTimeMinutes()).thenReturn(responseMinutes);
        lenient().when(policy.getResolutionTimeMinutes()).thenReturn(resolutionMinutes);
        return policy;
    }
}
