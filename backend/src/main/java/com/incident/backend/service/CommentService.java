package com.incident.backend.service;

import com.incident.backend.dto.CommentResponse;
import com.incident.backend.model.Comment;
import com.incident.backend.model.Incident;
import com.incident.backend.model.User;
import com.incident.backend.repository.CommentRepository;
import com.incident.backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final IncidentAccess access;

    public CommentService(CommentRepository commentRepository, UserRepository userRepository,
                          IncidentAccess access) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.access = access;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> list(Long incidentId, User current) {
        access.requireViewable(incidentId, current);
        return commentRepository.findByIncidentIdOrderByCreatedAtAsc(incidentId)
                .stream().map(CommentResponse::from).toList();
    }

    public CommentResponse add(Long incidentId, String text, User current) {
        Incident incident = access.requireViewable(incidentId, current);
        Comment comment = new Comment();
        comment.setIncident(incident);
        comment.setAuthor(userRepository.getReferenceById(current.getId()));
        comment.setText(text.trim());
        return CommentResponse.from(commentRepository.save(comment));
    }
}
