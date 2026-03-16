package com.bookcrossing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requester_id")
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(columnDefinition = "TEXT")
    private String ownerResponse;

    private LocalDateTime requestedAt;
    private LocalDateTime respondedAt;
    private LocalDateTime bookedUntil;

    @PrePersist
    public void prePersist() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
    }

    @Getter
    public enum BookingStatus {
        PENDING("Ожидает"),
        ACCEPTED("Одобрена"),
        REJECTED("Отклонена"),
        CANCELLED("Отменена"),
        COMPLETED("Завершена");

        private final String displayName;
        BookingStatus(String n) { this.displayName = n; }
    }
}