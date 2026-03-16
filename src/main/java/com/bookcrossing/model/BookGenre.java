package com.bookcrossing.model;

import lombok.Getter;

@Getter
public enum BookGenre {
    FICTION("Художественная литература"),
    SCIENCE("Научная литература"),
    FANTASY("Фантастика"),
    HISTORY("История"),
    BIOGRAPHY("Биографии"),
    BUSINESS("Бизнес"),
    KIDS("Детские книги"),
    OTHER("Другое");

    private final String displayName;

    BookGenre(String displayName) {
        this.displayName = displayName;
    }
}