package com.bookcrossing.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.List;

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

    // ── Роль ─────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    // ── Блокировка ───────────────────────────────────────────
    @Column(name = "is_blocked")
    private Boolean blocked = false;

    @Column(columnDefinition = "TEXT")
    private String blockReason;

    private LocalDateTime blockUntil;

    // ── Дата регистрации ─────────────────────────────────────
    private LocalDateTime registeredAt;

    // ── Книги ────────────────────────────────────────────────
    @OneToMany(mappedBy = "owner", cascade = CascadeType.ALL)
    @JsonIgnore
    private List<Book> books;

    // ── Авто-заполнение при создании ─────────────────────────
    @PrePersist
    public void prePersist() {
        if (registeredAt == null) registeredAt = LocalDateTime.now();
        if (role == null) role = UserRole.USER;
        if (blocked == null) blocked = false;
    }

    // ── Вспомогательные методы ───────────────────────────────

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

    // ── Enum ─────────────────────────────────────────────────
    public enum UserRole { USER, MODERATOR, ADMIN }

    // ── Getters / Setters ─────────────────────────────────────

    public Long getId()                      { return id; }
    public void setId(Long id)               { this.id = id; }

    public String getUsername()              { return username; }
    public void setUsername(String u)        { this.username = u; }

    public String getEmail()                 { return email; }
    public void setEmail(String e)           { this.email = e; }

    public String getPassword()              { return password; }
    public void setPassword(String p)        { this.password = p; }

    public String getFullName()              { return fullName; }
    public void setFullName(String f)        { this.fullName = f; }

    public String getAvatarUrl()             { return avatarUrl; }
    public void setAvatarUrl(String a)       { this.avatarUrl = a; }

    public LocalDate getBirthDate()          { return birthDate; }
    public void setBirthDate(LocalDate d)    { this.birthDate = d; }

    public String getCity()                  { return city; }
    public void setCity(String c)            { this.city = c; }

    public String getCountry()               { return country; }
    public void setCountry(String c)         { this.country = c; }

    public String getGender()                { return gender; }
    public void setGender(String g)          { this.gender = g; }

    public String getAboutMe()               { return aboutMe; }
    public void setAboutMe(String a)         { this.aboutMe = a; }

    public String getSocialLinks()           { return socialLinks; }
    public void setSocialLinks(String s)     { this.socialLinks = s; }

    public String getFavoriteGenres()        { return favoriteGenres; }
    public void setFavoriteGenres(String f)  { this.favoriteGenres = f; }

    public UserRole getRole()                { return role; }
    public void setRole(UserRole r)          { this.role = r; }

    public Boolean getBlocked()              { return blocked; }
    public void setBlocked(Boolean b)        { this.blocked = b; }

    public String getBlockReason()           { return blockReason; }
    public void setBlockReason(String r)     { this.blockReason = r; }

    public LocalDateTime getBlockUntil()     { return blockUntil; }
    public void setBlockUntil(LocalDateTime t){ this.blockUntil = t; }

    public LocalDateTime getRegisteredAt()   { return registeredAt; }
    public void setRegisteredAt(LocalDateTime t){ this.registeredAt = t; }

    public List<Book> getBooks()             { return books; }
    public void setBooks(List<Book> books)   { this.books = books; }
}