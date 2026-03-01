package com.bookcrossing.repository;

import com.bookcrossing.model.Book;
import com.bookcrossing.model.Review;
import com.bookcrossing.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByTargetUser(User targetUser);
    List<Review> findByBookId(Long bookId);

    // Проверка: есть ли отзыв конкретного пользователя на конкретную книгу
    Optional<Review> findByBookAndUser(Book book, User user);

    // Отзывы пользователя для статистики/профиля
    List<Review> findByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.book.id = :bookId")
    Double findAverageRatingByBookId(@Param("bookId") Long bookId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.book.id = :bookId")
    Long countByBookId(@Param("bookId") Long bookId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.user = :user")
    long countByUser(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM Review r WHERE r.book.id = :bookId")
    void deleteByBookId(@Param("bookId") Long bookId);
}