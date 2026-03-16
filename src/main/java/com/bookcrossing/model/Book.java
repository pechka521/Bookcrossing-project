package com.bookcrossing.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;

@Data
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Название книги не может быть пустым")
    private String title;

    @NotBlank(message = "Автор обязателен")
    private String author;

    @Size(max = 1000, message = "Описание слишком длинное")
    private String description;

    private String genre;

    @Column(columnDefinition = "TEXT")
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    private BookStatus status = BookStatus.FREE;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User owner;

    @Getter
    public enum BookStatus {
        FREE("Свободна"),
        BUSY("Занята"),
        BOOKED("Забронирована");

        private final String displayValue;
        BookStatus(String displayValue) { this.displayValue = displayValue; }
    }

    public String getImageDisplay() {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return "https://via.placeholder.com/150?text=No+Cover";
        }
        return imageUrl;
    }
}