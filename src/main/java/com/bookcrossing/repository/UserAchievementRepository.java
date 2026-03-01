package com.bookcrossing.repository;

import com.bookcrossing.model.Achievement;
import com.bookcrossing.model.User;
import com.bookcrossing.model.UserAchievement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, Long> {

    List<UserAchievement> findByUserOrderByEarnedAtDesc(User user);

    boolean existsByUserAndAchievement(User user, Achievement achievement);

    @Query("SELECT ua.achievement.code FROM UserAchievement ua WHERE ua.user = :user")
    Set<String> findEarnedCodesByUser(@Param("user") User user);

    // Выставленные на витрину
    List<UserAchievement> findByUserAndFeaturedTrueOrderByEarnedAtDesc(User user);

    // Количество featured достижений пользователя
    long countByUserAndFeaturedTrue(User user);

    // Найти конкретную запись
    Optional<UserAchievement> findByUserAndAchievement(User user, Achievement achievement);
}