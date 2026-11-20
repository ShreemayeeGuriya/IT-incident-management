# 1. Problem analysis

## Context

Organizations receive IT incident reports through inconsistent channels and descriptions. Without shared records, reporters cannot track progress, support staff can miss ownership or urgency, and managers lack a clear view of workload and service targets. The OOP project brief asks for an incident management system that handles the lifecycle and demonstrates object-oriented design.

## Stakeholders

| Stakeholder | Need | Current application support |
|---|---|---|
| Employee | Report an interruption, track it, communicate with support | Create/view own incidents, comment, receive in-app notifications |
| Technician | Triage, own, investigate, resolve and track workload | Shared queue, assignment/state changes, comments, workload view |
| Administrator | Govern access and operational decision settings | Users/roles, skills, strategy selection, reference data views |
| Service owner | Meet response/resolution goals and understand performance | SLA policies, per-incident objective status, escalation, dashboard |

Service owner is an analytical stakeholder, not a separate application role.

## Problem statement and objectives

The system must maintain a durable, auditable record from report through closure; make priority and ownership decisions consistently; expose progress to reporters/support; and surface SLA risk. It should reduce duplicate effort with similar-incident suggestions and provide role-specific views.

- Capture description, category, affected service, impact, urgency, and business criticality.
- Guard the lifecycle and preserve change history.
- Offer configurable priority and technician assignment strategies.
- Track response and resolution objectives independently and escalate breaches.
- Support search, comments, notifications, dashboards, and duplicate suggestions.
- Demonstrate encapsulation, inheritance, polymorphism, interfaces, and design patterns.

## Constraints and assumptions

The repository implements a React/TypeScript SPA, Spring Boot REST API, and PostgreSQL persistence. Email is simulated, not sent through a configured provider. Reference data is seeded in the database; Admin reference views are read-only. Similar incident matching is a deterministic text heuristic, not an embedding or machine learning service. The requested demo video is not in the repository.

## Success measures

An employee can submit and view an incident; support can prioritize, assign, investigate, resolve, verify, and close it subject to guards; records persist with comments/audit/notifications; and tests cover key rules and edge cases. Deployment still requires environment-specific database, JWT, and notification configuration.
