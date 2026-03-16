package com.bookcrossing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String email;

    @JsonIgnore
    private String password;

    private String fullName;

    @Column(columnDefinition = "TEXT")
    private String avatarUrl;

    private LocalDate birthDate;
    private String city;
    private String country;
    private String gender;

    @Column(columnDefinition = "TEXT")
    private String aboutMe;

    private String socialLinks;
    private String favoriteGenres;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @Column(name = "is_blocked")
    private Boolean blocked = false;

    @Column(columnDefinition = "TEXT")
    private String blockReason;

    private LocalDateTime blockUntil;

    private LocalDateTime registeredAt;

    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Book> books;

    @PrePersist
    public void prePersist() {
        if (registeredAt == null) registeredAt = LocalDateTime.now();
        if (role == null) role = UserRole.USER;
        if (blocked == null) blocked = false;
    }

    // ── Вспомогательные методы (не заменяются Lombok) ─────────

    public Integer getAge() {
        if (birthDate == null) return null;
        return Period.between(birthDate, LocalDate.now()).getYears();
    }

    public String getAvatarDisplay() {
        if (avatarUrl == null || avatarUrl.isEmpty()) return "/images/usual_avatar.png";
        return avatarUrl;
    }

    public boolean isCurrentlyBlocked() {
        if (blocked == null || !blocked) return false;
        if (blockUntil == null) return true;
        return LocalDateTime.now().isBefore(blockUntil);
    }

    public boolean isAdmin()     { return role == UserRole.ADMIN; }
    public boolean isModerator() { return role == UserRole.MODERATOR || role == UserRole.ADMIN; }

    public enum UserRole { USER, MODERATOR, ADMIN }
}