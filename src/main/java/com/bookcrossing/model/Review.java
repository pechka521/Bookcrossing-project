package com.bookcrossing.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reviews",
        uniqueConstraints = @UniqueConstraint(columnNames = {"book_id", "user_id"}))
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Кто написал отзыв
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"books","password","email","blockReason","blockUntil"})
    private User user;

    // На какую книгу
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    @JsonIgnoreProperties({"owner"})
    private Book book;

    // Владелец книги (для удобства уведомлений)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id")
    @JsonIgnoreProperties({"books","password","email","blockReason","blockUntil"})
    private User targetUser;

    @Column(nullable = false)
    private int rating;  // 1..5

    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Getters / Setters ─────────────────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public User getUser()                        { return user; }
    public void setUser(User user)               { this.user = user; }

    public Book getBook()                        { return book; }
    public void setBook(Book book)               { this.book = book; }

    public User getTargetUser()                  { return targetUser; }
    public void setTargetUser(User targetUser)   { this.targetUser = targetUser; }

    public int getRating()                       { return rating; }
    public void setRating(int rating)            { this.rating = rating; }

    public String getComment()                   { return comment; }
    public void setComment(String comment)       { this.comment = comment; }

    public LocalDateTime getCreatedAt()          { return createdAt; }
    public void setCreatedAt(LocalDateTime t)    { this.createdAt = t; }

    public LocalDateTime getUpdatedAt()          { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t)    { this.updatedAt = t; }
}