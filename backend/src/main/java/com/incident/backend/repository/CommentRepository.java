package com.incident.backend.repository;

import com.incident.backend.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Integer> {

    List<Comment> findByIncidentIdOrderByCreatedAtAsc(Integer incidentId);

    @Query("select distinct c.author.id from Comment c where c.incident.id = :incidentId")
    List<Integer> findDistinctAuthorIdsByIncidentId(@Param("incidentId") Integer incidentId);
}
