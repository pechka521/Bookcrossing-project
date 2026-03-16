package com.bookcrossing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "moderation_logs")
public class ModerationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "moderator_id")
    private User moderator;

    @Enumerated(EnumType.STRING)
    private ActionType action;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @ManyToOne
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    private Long bookId;
    private String bookTitle;

    private LocalDateTime createdAt;

    @Getter
    public enum ActionType {
        BOOK_DELETED("Удаление книги"),
        USER_BLOCKED("Блокировка пользователя"),
        USER_UNBLOCKED("Разблокировка пользователя"),
        ROLE_CHANGED("Изменение роли"),
        USER_DELETED("Удаление аккаунта"),
        COMPLAINT_ACCEPTED("Жалоба принята"),
        COMPLAINT_REJECTED("Жалоба отклонена");

        private final String displayName;
        ActionType(String n) { this.displayName = n; }
    }
}