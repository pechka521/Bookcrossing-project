package com.bookcrossing.service;

import com.bookcrossing.model.*;
import com.bookcrossing.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AchievementService")
class AchievementServiceTest {

    @Mock AchievementRepository       achievementRepository;
    @Mock UserAchievementRepository   userAchievementRepository;
    @Mock BookRepository              bookRepository;
    @Mock ReviewRepository            reviewRepository;
    @Mock ComplaintRepository         complaintRepository;
    @Mock NotificationService         notificationService;
    @InjectMocks AchievementService   achievementService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
    }

    // ─── calculateStats ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateStats")
    class CalculateStats {

        @Test
        @DisplayName("Нет книг, отзывов, жалоб — все нули")
        void noActivity_allZeros() {
            when(bookRepository.findByOwner(user)).thenReturn(List.of());
            when(reviewRepository.countByUser(user)).thenReturn(0L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);

            AchievementService.UserStats stats = achievementService.calculateStats(user);

            assertThat(stats.getBooksAdded()).isZero();
            assertThat(stats.getBooksGiven()).isZero();
            assertThat(stats.getReviewsWritten()).isZero();
            assertThat(stats.getComplaintsSent()).isZero();
        }

        @Test
        @DisplayName("3 книги, 1 BUSY — booksGiven=1, booksAdded=3")
        void mixedBooks_correctCounts() {
            Book free = makeBook(Book.BookStatus.FREE);
            Book busy1 = makeBook(Book.BookStatus.BUSY);
            Book booked = makeBook(Book.BookStatus.BOOKED);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(free, busy1, booked));
            when(reviewRepository.countByUser(user)).thenReturn(2L);
            when(complaintRepository.countByAuthor(user)).thenReturn(1L);

            AchievementService.UserStats stats = achievementService.calculateStats(user);

            assertThat(stats.getBooksAdded()).isEqualTo(3);
            assertThat(stats.getBooksGiven()).isEqualTo(1);  // только BUSY
            assertThat(stats.getReviewsWritten()).isEqualTo(2);
            assertThat(stats.getComplaintsSent()).isEqualTo(1);
        }

        @Test
        @DisplayName("Дата регистрации null — daysInSystem = 0")
        void noRegistrationDate_zeroDays() {
            user.setRegisteredAt(null);
            when(bookRepository.findByOwner(user)).thenReturn(List.of());
            when(reviewRepository.countByUser(user)).thenReturn(0L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);

            AchievementService.UserStats stats = achievementService.calculateStats(user);

            assertThat(stats.getDaysInSystem()).isZero();
        }

        @Test
        @DisplayName("registeredAt установлен — daysInSystem > 0")
        void withRegistration_positiveDays() {
            user.setRegisteredAt(LocalDateTime.now().minusDays(10));
            when(bookRepository.findByOwner(user)).thenReturn(List.of());
            when(reviewRepository.countByUser(user)).thenReturn(0L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);

            AchievementService.UserStats stats = achievementService.calculateStats(user);

            assertThat(stats.getDaysInSystem()).isGreaterThanOrEqualTo(10);
        }
    }

    // ─── calculateRank ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("calculateRank (через calculateStats)")
    class CalculateRank {

        @Test @DisplayName("0 активности — Новичок")
        void rank_zero() {
            stubStatsFor(0, 0, 0, 0);
            assertThat(achievementService.calculateStats(user).getRank()).contains("Новичок");
        }

        @Test @DisplayName("1 книга — Начинающий")
        void rank_beginner() {
            stubStatsFor(1, 0, 0, 0);
            assertThat(achievementService.calculateStats(user).getRank()).contains("Начинающий");
        }

        @Test @DisplayName("5 книг — Книгочей")
        void rank_bookworm() {
            stubStatsFor(5, 0, 0, 0);
            assertThat(achievementService.calculateStats(user).getRank()).contains("Книгочей");
        }

        @Test @DisplayName("10+ баллов — Активный читатель")
        void rank_active() {
            stubStatsFor(10, 0, 0, 0);
            assertThat(achievementService.calculateStats(user).getRank()).contains("Активный читатель");
        }

        @Test @DisplayName("25+ баллов — Меценат")
        void rank_patron() {
            stubStatsFor(25, 0, 0, 0);
            assertThat(achievementService.calculateStats(user).getRank()).contains("Меценат");
        }

        @Test @DisplayName("50+ баллов — Хранитель знаний")
        void rank_legend() {
            stubStatsFor(50, 0, 0, 0);
            assertThat(achievementService.calculateStats(user).getRank()).contains("Хранитель");
        }

        private void stubStatsFor(long added, long given, long reviews, long complaints) {
            List<Book> books = new java.util.ArrayList<>();
            for (int i = 0; i < added; i++) books.add(makeBook(Book.BookStatus.FREE));
            for (int i = 0; i < given; i++) books.add(makeBook(Book.BookStatus.BUSY));
            when(bookRepository.findByOwner(user)).thenReturn(books);
            when(reviewRepository.countByUser(user)).thenReturn(reviews);
            when(complaintRepository.countByAuthor(user)).thenReturn(complaints);
        }
    }

    // ─── checkAndAward ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("checkAndAward")
    class CheckAndAward {

        @Test
        @DisplayName("Достижение уже получено — не начисляется снова")
        void alreadyEarned_notAwarded() {
            Achievement a = makeAchievement("FIRST_BOOK", Achievement.AchievementType.BOOKS_ADDED, 1);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(makeBook(Book.BookStatus.FREE)));
            when(reviewRepository.countByUser(user)).thenReturn(0L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);
            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findEarnedCodesByUser(user)).thenReturn(Set.of("FIRST_BOOK"));

            achievementService.checkAndAward(user);

            verify(userAchievementRepository, never()).save(any());
            verify(notificationService, never()).sendNotification(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Пользователь добавил первую книгу — получает FIRST_BOOK")
        void newBook_awardsFirstBook() {
            Achievement a = makeAchievement("FIRST_BOOK", Achievement.AchievementType.BOOKS_ADDED, 1);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(makeBook(Book.BookStatus.FREE)));
            when(reviewRepository.countByUser(user)).thenReturn(0L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);
            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findEarnedCodesByUser(user)).thenReturn(Set.of());

            achievementService.checkAndAward(user);

            verify(userAchievementRepository).save(any(UserAchievement.class));
            verify(notificationService).sendNotification(eq("alice"), contains("достижение"), any(), any());
        }

        @Test
        @DisplayName("REVIEWS_WRITTEN — получает при достижении порога")
        void reviews_awardsWhenThresholdMet() {
            Achievement a = makeAchievement("FIRST_REVIEW", Achievement.AchievementType.REVIEWS_WRITTEN, 1);
            when(bookRepository.findByOwner(user)).thenReturn(List.of());
            when(reviewRepository.countByUser(user)).thenReturn(1L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);
            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findEarnedCodesByUser(user)).thenReturn(Set.of());

            achievementService.checkAndAward(user);

            verify(userAchievementRepository).save(any());
        }

        @Test
        @DisplayName("REVIEWS_WRITTEN — не достигнут порог — не выдаётся")
        void reviews_belowThreshold_notAwarded() {
            Achievement a = makeAchievement("REVIEWER_5", Achievement.AchievementType.REVIEWS_WRITTEN, 5);
            when(bookRepository.findByOwner(user)).thenReturn(List.of());
            when(reviewRepository.countByUser(user)).thenReturn(3L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);
            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findEarnedCodesByUser(user)).thenReturn(Set.of());

            achievementService.checkAndAward(user);

            verify(userAchievementRepository, never()).save(any());
        }

        @Test
        @DisplayName("DAYS_IN_SYSTEM — выдаётся при нужном числе дней")
        void daysInSystem_awards() {
            user.setRegisteredAt(LocalDateTime.now().minusDays(35));
            Achievement a = makeAchievement("VETERAN_30", Achievement.AchievementType.DAYS_IN_SYSTEM, 30);
            when(bookRepository.findByOwner(user)).thenReturn(List.of());
            when(reviewRepository.countByUser(user)).thenReturn(0L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);
            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findEarnedCodesByUser(user)).thenReturn(Set.of());

            achievementService.checkAndAward(user);

            verify(userAchievementRepository).save(any());
        }

        @Test
        @DisplayName("COMPLAINTS_SENT — выдаётся при наличии жалоб")
        void complaints_awards() {
            Achievement a = makeAchievement("COMPLAINER", Achievement.AchievementType.COMPLAINTS_SENT, 1);
            when(bookRepository.findByOwner(user)).thenReturn(List.of());
            when(reviewRepository.countByUser(user)).thenReturn(0L);
            when(complaintRepository.countByAuthor(user)).thenReturn(1L);
            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findEarnedCodesByUser(user)).thenReturn(Set.of());

            achievementService.checkAndAward(user);

            verify(userAchievementRepository).save(any());
        }

        @Test
        @DisplayName("award — UserAchievement сохраняется с правильным пользователем")
        void award_savesCorrectUserAchievement() {
            Achievement a = makeAchievement("FIRST_BOOK", Achievement.AchievementType.BOOKS_ADDED, 1);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(makeBook(Book.BookStatus.FREE)));
            when(reviewRepository.countByUser(user)).thenReturn(0L);
            when(complaintRepository.countByAuthor(user)).thenReturn(0L);
            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findEarnedCodesByUser(user)).thenReturn(Set.of());

            achievementService.checkAndAward(user);

            ArgumentCaptor<UserAchievement> cap = ArgumentCaptor.forClass(UserAchievement.class);
            verify(userAchievementRepository).save(cap.capture());
            assertThat(cap.getValue().getUser()).isEqualTo(user);
            assertThat(cap.getValue().getAchievement()).isEqualTo(a);
            assertThat(cap.getValue().getEarnedAt()).isNotNull();
        }
    }

    // ─── getUserAchievements ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserAchievements")
    class GetUserAchievements {

        @Test
        @DisplayName("Нет достижений — все earned=false")
        void noEarned_allFalse() {
            Achievement a = makeAchievement("FIRST_BOOK", Achievement.AchievementType.BOOKS_ADDED, 1);
            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findByUserOrderByEarnedAtDesc(user)).thenReturn(List.of());

            List<AchievementService.AchievementDto> dtos = achievementService.getUserAchievements(user);

            assertThat(dtos).hasSize(1);
            assertThat(dtos.get(0).earned()).isFalse();
            assertThat(dtos.get(0).earnedAt()).isNull();
        }

        @Test
        @DisplayName("Есть полученное достижение — earned=true, earnedAt заполнен")
        void withEarned_earnedTrue() {
            Achievement a = makeAchievement("FIRST_BOOK", Achievement.AchievementType.BOOKS_ADDED, 1);
            UserAchievement ua = new UserAchievement();
            ua.setAchievement(a);
            ua.setUser(user);
            ua.setEarnedAt(LocalDateTime.now());
            ua.setFeatured(false);

            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findByUserOrderByEarnedAtDesc(user)).thenReturn(List.of(ua));

            List<AchievementService.AchievementDto> dtos = achievementService.getUserAchievements(user);

            assertThat(dtos.get(0).earned()).isTrue();
            assertThat(dtos.get(0).earnedAt()).isNotNull();
        }

        @Test
        @DisplayName("Featured achievement — featured=true в DTO")
        void featuredAchievement_featuredTrue() {
            Achievement a = makeAchievement("FIRST_BOOK", Achievement.AchievementType.BOOKS_ADDED, 1);
            UserAchievement ua = new UserAchievement();
            ua.setAchievement(a);
            ua.setUser(user);
            ua.setEarnedAt(LocalDateTime.now());
            ua.setFeatured(true);

            when(achievementRepository.findAllByOrderById()).thenReturn(List.of(a));
            when(userAchievementRepository.findByUserOrderByEarnedAtDesc(user)).thenReturn(List.of(ua));

            List<AchievementService.AchievementDto> dtos = achievementService.getUserAchievements(user);

            assertThat(dtos.get(0).featured()).isTrue();
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Achievement makeAchievement(String code, Achievement.AchievementType type, int value) {
        Achievement a = new Achievement();
        a.setId(1L);
        a.setCode(code);
        a.setTitle("Test " + code);
        a.setDescription("desc");
        a.setIcon("⭐");
        a.setType(type);
        a.setConditionValue(value);
        return a;
    }

    private Book makeBook(Book.BookStatus status) {
        Book b = new Book();
        b.setStatus(status);
        return b;
    }
}