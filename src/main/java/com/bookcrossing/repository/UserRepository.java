package com.bookcrossing.repository;

import com.bookcrossing.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    List<User> findByRole(User.UserRole role);

    @Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%',:q,'%'))" +
            " OR LOWER(u.email) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<User> searchUsers(@Param("q") String query);

    // ── Каскадная очистка (нативный SQL) ────────────────────

    @Modifying
    @Query(value = "DELETE FROM notifications WHERE username = :username", nativeQuery = true)
    void deleteNotificationsByUsername(@Param("username") String username);

    @Modifying
    @Query(value = "DELETE FROM messages WHERE sender_id = :uid OR recipient_id = :uid", nativeQuery = true)
    void deleteMessagesByUser(@Param("uid") Long uid);

    @Modifying
    @Query(value = "DELETE FROM reviews WHERE user_id = :uid", nativeQuery = true)
    void deleteReviewsByUser(@Param("uid") Long uid);

    @Modifying
    @Query(value = "DELETE FROM reviews WHERE target_user_id = :uid", nativeQuery = true)
    void deleteReviewsTargetingUser(@Param("uid") Long uid);

    @Modifying
    @Query(value = "DELETE FROM reviews WHERE book_id IN (SELECT id FROM books WHERE user_id = :uid)", nativeQuery = true)
    void deleteReviewsOfUserBooks(@Param("uid") Long uid);

    @Modifying
    @Query(value = "DELETE FROM books WHERE user_id = :uid", nativeQuery = true)
    void deleteBooksByUser(@Param("uid") Long uid);
}