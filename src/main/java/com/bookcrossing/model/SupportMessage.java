package com.bookcrossing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Сообщение в поддержку от пользователя администратору.
 */
@Getter
@Setter
@Entity
@Table(name = "support_messages")
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    /** Тема / тип обращения */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportType type = SupportType.OTHER;

    /** Текст обращения */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String message;

    /** Статус обращения */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SupportStatus status = SupportStatus.NEW;

    /** Ответ/комментарий администратора */
    @Column(columnDefinition = "TEXT")
    private String adminReply;

    private LocalDateTime createdAt;
    private LocalDateTime repliedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public enum SupportType {
        SUGGESTION("Пожелание"),
        COMPLAINT("Жалоба"),
        BUG("Ошибка в системе"),
        QUESTION("Вопрос"),
        OTHER("Другое");

        private final String displayName;
        SupportType(String n) { this.displayName = n; }
        public String getDisplayName() { return displayName; }
    }

    public enum SupportStatus {
        NEW("Новое"),
        IN_PROGRESS("В обработке"),
        RESOLVED("Решено");

        private final String displayName;
        SupportStatus(String n) { this.displayName = n; }
        public String getDisplayName() { return displayName; }
    }
}