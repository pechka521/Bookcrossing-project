package com.bookcrossing.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_achievements",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "achievement_id"}))
public class UserAchievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "achievement_id", nullable = false)
    private Achievement achievement;

    private LocalDateTime earnedAt;

    // Выставлено на витрину (максимум 3 на пользователя)
    @Column(nullable = false)
    private boolean featured = false;

    // ── Getters / Setters ─────────────────────────────────────

    public Long getId()                          { return id; }
    public void setId(Long id)                   { this.id = id; }

    public User getUser()                        { return user; }
    public void setUser(User user)               { this.user = user; }

    public Achievement getAchievement()          { return achievement; }
    public void setAchievement(Achievement a)    { this.achievement = a; }

    public LocalDateTime getEarnedAt()           { return earnedAt; }
    public void setEarnedAt(LocalDateTime t)     { this.earnedAt = t; }

    public boolean isFeatured()                  { return featured; }
    public void setFeatured(boolean featured)    { this.featured = featured; }
}