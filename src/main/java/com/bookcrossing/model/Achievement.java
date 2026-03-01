package com.bookcrossing.model;

import jakarta.persistence.*;

@Entity
@Table(name = "achievements")
public class Achievement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;          // уникальный ключ, напр. "FIRST_BOOK"

    @Column(nullable = false)
    private String title;         // "Первопроходец"

    @Column(columnDefinition = "TEXT")
    private String description;   // "Добавил первую книгу"

    private String icon;          // эмодзи-иконка

    @Column(columnDefinition = "TEXT")
    private String condition;     // текст условия для тултипа

    private Integer conditionValue; // пороговое число (напр. 5 для "5 книг")

    @Enumerated(EnumType.STRING)
    private AchievementType type;  // тип события

    public enum AchievementType {
        BOOKS_ADDED,        // книги добавлены в каталог
        BOOKS_GIVEN,        // книги отмечены как "занята" (переданы)
        REVIEWS_WRITTEN,    // написано отзывов
        DAYS_IN_SYSTEM,     // дней в системе
        COMPLAINTS_SENT     // жалоб отправлено (участие в модерации)
    }

    // ── Getters / Setters ─────────────────────────────────────

    public Long getId()                     { return id; }
    public void setId(Long id)              { this.id = id; }

    public String getCode()                 { return code; }
    public void setCode(String code)        { this.code = code; }

    public String getTitle()                { return title; }
    public void setTitle(String title)      { this.title = title; }

    public String getDescription()          { return description; }
    public void setDescription(String d)    { this.description = d; }

    public String getIcon()                 { return icon; }
    public void setIcon(String icon)        { this.icon = icon; }

    public String getCondition()            { return condition; }
    public void setCondition(String c)      { this.condition = c; }

    public Integer getConditionValue()      { return conditionValue; }
    public void setConditionValue(Integer v){ this.conditionValue = v; }

    public AchievementType getType()        { return type; }
    public void setType(AchievementType t)  { this.type = t; }
}