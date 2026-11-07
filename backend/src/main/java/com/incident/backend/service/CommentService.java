package com.incident.backend.service;

import com.incident.backend.events.CommentCreatedEvent;
import com.incident.backend.dto.CommentResponse;
import com.incident.backend.model.Comment;
import com.incident.backend.model.Incident;
import com.incident.backend.model.User;
import com.incident.backend.repository.CommentRepository;
import com.incident.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final IncidentAccess access;
    private final ApplicationEventPublisher eventPublisher;

    public CommentService(CommentRepository commentRepository, UserRepository userRepository,
                          IncidentAccess access, ApplicationEventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.access = access;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long incidentId, User current) {
        access.requireViewable(incidentId, current);
        return commentRepository.findByIncidentIdOrderByCreatedAtAsc(Math.toIntExact(incidentId))
                .stream().map(CommentResponse::from).toList();
    }

    public CommentResponse add(Long incidentId, String text, User current) {
        Incident incident = access.requireViewable(incidentId, current);
        Comment comment = new Comment();
        comment.setIncident(incident);
        comment.setAuthor(userRepository.getReferenceById(current.getId()));
        comment.setText(text.trim());
        Comment saved = commentRepository.saveAndFlush(comment);
        eventPublisher.publishEvent(new CommentCreatedEvent(UUID.randomUUID(), incident.getId(), current.getId(),
                saved.getCreatedAt() == null ? Instant.now() : saved.getCreatedAt(), saved.getId()));
        return CommentResponse.from(saved);
    }
}
