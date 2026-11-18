package com.incident.backend.priority;

import com.incident.backend.model.Priority;

/**
 * The result of a priority calculation, kept together with how it was reached so the decision
 * can be shown to users and recorded in the audit trail.
 *
 * @param priority    the calculated priority
 * @param score       the numeric score the strategy produced (meaning depends on the strategy)
 * @param strategy    name of the strategy that produced it
 * @param explanation one-line reason, e.g. {@code "3 x 3 x 2 = 18 -> P1"}
 */
public record PriorityAssessment(Priority priority, int score, String strategy, String explanation) { }
