package com.bookcrossing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    public enum BookingStatus {
        PENDING("Ожидает"),
        ACCEPTED("Одобрена"),
        REJECTED("Отклонена"),
        CANCELLED("Отменена"),
        COMPLETED("Завершена");

        private final String displayName;
        BookingStatus(String n) { this.displayName = n; }
        public String getDisplayName() { return displayName; }
    }

    @PrePersist
    public void prePersist() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
    }

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }
    public Book getBook()                        { return book; }
    public void setBook(Book b)                  { this.book = b; }
    public User getRequester()                   { return requester; }
    public void setRequester(User u)             { this.requester = u; }
    public User getOwner()                       { return owner; }
    public void setOwner(User u)                 { this.owner = u; }
    public BookingStatus getStatus()             { return status; }
    public void setStatus(BookingStatus s)       { this.status = s; }
    public String getMessage()                   { return message; }
    public void setMessage(String m)             { this.message = m; }
    public String getOwnerResponse()             { return ownerResponse; }
    public void setOwnerResponse(String r)       { this.ownerResponse = r; }
    public LocalDateTime getRequestedAt()        { return requestedAt; }
    public void setRequestedAt(LocalDateTime t)  { this.requestedAt = t; }
    public LocalDateTime getRespondedAt()        { return respondedAt; }
    public void setRespondedAt(LocalDateTime t)  { this.respondedAt = t; }
    public LocalDateTime getBookedUntil()        { return bookedUntil; }
    public void setBookedUntil(LocalDateTime t)  { this.bookedUntil = t; }
}