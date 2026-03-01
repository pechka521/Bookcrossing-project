package com.bookcrossing.repository;

import com.bookcrossing.model.Complaint;
import com.bookcrossing.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findAllByOrderByCreatedAtDesc();

    List<Complaint> findByStatusOrderByCreatedAtDesc(Complaint.ComplaintStatus status);

    long countByStatus(Complaint.ComplaintStatus status);

    @Query("SELECT COUNT(c) FROM Complaint c WHERE c.resolvedBy.username = :username")
    long countResolvedByModerator(@Param("username") String username);

    // Для достижений — жалобы, отправленные пользователем
    long countByAuthor(User author);
}