package com.bookcrossing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "author_id")
    private User author;

    @ManyToOne
    @JoinColumn(name = "target_user_id")
    private User targetUser;

    private Long targetBookId;
    private String targetBookTitle;

    @Enumerated(EnumType.STRING)
    private ComplaintType type;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private ComplaintStatus status = ComplaintStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String moderatorComment;

    @ManyToOne
    @JoinColumn(name = "resolved_by_id")
    private User resolvedBy;

    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;

    @Getter
    public enum ComplaintType {
        SPAM("Спам"),
        INAPPROPRIATE_CONTENT("Неприемлемый контент"),
        FAKE("Фейковое объявление"),
        SCAM("Мошенничество"),
        OTHER("Другое");

        private final String displayName;
        ComplaintType(String n) { this.displayName = n; }
    }

    @Getter
    public enum ComplaintStatus {
        PENDING("На рассмотрении"),
        ACCEPTED("Принята"),
        REJECTED("Отклонена");

        private final String displayName;
        ComplaintStatus(String n) { this.displayName = n; }
    }
}