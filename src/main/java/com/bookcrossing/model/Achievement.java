package com.bookcrossing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "achievements")
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String icon;

    @Column(columnDefinition = "TEXT")
    private String condition;

    private Integer conditionValue;

    @Enumerated(EnumType.STRING)
    private AchievementType type;

    @Getter
    public enum AchievementType {
        BOOKS_ADDED("Книги добавлены"),
        BOOKS_GIVEN("Книги переданы"),
        REVIEWS_WRITTEN("Написано отзывов"),
        DAYS_IN_SYSTEM("Дней в системе"),
        COMPLAINTS_SENT("Жалоб отправлено");

        private final String displayName;
        AchievementType(String n) { this.displayName = n; }
    }
}