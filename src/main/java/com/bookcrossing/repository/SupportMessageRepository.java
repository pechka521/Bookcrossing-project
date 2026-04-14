package com.bookcrossing.repository;

import com.bookcrossing.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    List<SupportMessage> findAllByOrderByCreatedAtDesc();
    long countByStatus(SupportMessage.SupportStatus status);
}