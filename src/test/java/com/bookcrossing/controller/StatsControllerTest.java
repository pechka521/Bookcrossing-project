package com.bookcrossing.controller;

import com.bookcrossing.model.*;
import com.bookcrossing.repository.*;
import com.bookcrossing.service.AchievementService;
import com.bookcrossing.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsController")
class StatsControllerTest {

    @Mock UserService                userService;
    @Mock AchievementService         achievementService;
    @Mock BookRepository             bookRepository;
    @Mock ReviewRepository           reviewRepository;
    @Mock BookingRepository          bookingRepository;
    @Mock UserAchievementRepository  userAchievementRepository;
    @Mock AchievementRepository      achievementRepository;
    @InjectMocks StatsController statsController;

    private User      user;
    private Principal principal;

    @BeforeEach
    void setUp() {
        user = new User(); user.setId(1L); user.setUsername("alice");
        principal = () -> "alice";
    }

    // ─── getMyStats ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getMyStats")
    class GetMyStats {

        @Test
        @DisplayName("200 со статистикой")
        void returns200() {
            AchievementService.UserStats stats = new AchievementService.UserStats();
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementService.calculateStats(user)).thenReturn(stats);

            ResponseEntity<AchievementService.UserStats> resp = statsController.getMyStats(principal);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).isEqualTo(stats);
            verify(achievementService).checkAndAward(user);
        }

        @Test
        @DisplayName("checkAndAward вызывается перед возвратом")
        void checkAndAwardCalled() {
            AchievementService.UserStats stats = new AchievementService.UserStats();
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementService.calculateStats(user)).thenReturn(stats);

            statsController.getMyStats(principal);

            verify(achievementService).checkAndAward(user);
        }
    }

    // ─── getUserStats ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getUserStats")
    class GetUserStats {

        @Test
        @DisplayName("Пользователь найден — 200")
        void found_200() {
            AchievementService.UserStats stats = new AchievementService.UserStats();
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementService.calculateStats(user)).thenReturn(stats);

            ResponseEntity<?> resp = statsController.getUserStats("alice");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("Пользователь не найден — 404")
        void notFound_404() {
            when(userService.findByUsername("ghost")).thenThrow(new RuntimeException("not found"));

            ResponseEntity<?> resp = statsController.getUserStats("ghost");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─── getAchievements ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAchievements")
    class GetAchievements {

        @Test
        @DisplayName("Возвращает список DTO")
        void returnsList() {
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementService.getUserAchievements(user)).thenReturn(List.of());

            ResponseEntity<List<AchievementService.AchievementDto>> resp =
                    statsController.getAchievements(principal);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    // ─── getBooksAdded ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getBooksAdded")
    class GetBooksAdded {

        @Test
        @DisplayName("Пользователь существует — 200 со списком")
        void found_200() {
            Book b = new Book(); b.setId(1L); b.setTitle("T"); b.setAuthor("A");
            b.setStatus(Book.BookStatus.FREE);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(b));

            ResponseEntity<List<Map<String, Object>>> resp = statsController.getBooksAdded("alice");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("Пользователь не найден — 404")
        void notFound_404() {
            when(userService.findByUsername("ghost")).thenThrow(new RuntimeException());
            ResponseEntity<?> resp = statsController.getBooksAdded("ghost");
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Книга без обложки — imageDisplay — placeholder")
        void bookWithoutImage_usesPlaceholder() {
            Book b = new Book(); b.setId(1L); b.setTitle("T"); b.setAuthor("A");
            b.setStatus(Book.BookStatus.FREE); b.setImageUrl(null);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(b));

            ResponseEntity<List<Map<String, Object>>> resp = statsController.getBooksAdded("alice");

            assertThat(resp.getBody().get(0).get("image")).asString().contains("placeholder");
        }

        @Test
        @DisplayName("Все поля книги присутствуют в ответе")
        void allFieldsPresent() {
            Book b = new Book(); b.setId(5L); b.setTitle("War"); b.setAuthor("Tolstoy");
            b.setGenre("FICTION"); b.setStatus(Book.BookStatus.FREE);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(b));

            ResponseEntity<List<Map<String, Object>>> resp = statsController.getBooksAdded("alice");

            Map<String, Object> row = resp.getBody().get(0);
            assertThat(row).containsKeys("id", "title", "author", "genre", "status", "image");
            assertThat(row.get("title")).isEqualTo("War");
            assertThat(row.get("author")).isEqualTo("Tolstoy");
        }
    }

    // ─── getBooksGiven ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getBooksGiven")
    class GetBooksGiven {

        @Test
        @DisplayName("Возвращает только BUSY книги")
        void onlyBusyBooks() {
            Book free = new Book(); free.setId(1L); free.setTitle("F");
            free.setStatus(Book.BookStatus.FREE);
            Book busy = new Book(); busy.setId(2L); busy.setTitle("B");
            busy.setStatus(Book.BookStatus.BUSY);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(free, busy));
            when(bookingRepository.findActiveBookingForBook(eq(busy), any()))
                    .thenReturn(Optional.empty());

            ResponseEntity<List<Map<String, Object>>> resp = statsController.getBooksGiven("alice");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).hasSize(1);
            assertThat(resp.getBody().get(0).get("title")).isEqualTo("B");
        }

        @Test
        @DisplayName("Активная бронь добавляет bookedBy, bookedSince, bookedUntil")
        void activeBooking_addsBookingInfo() {
            Book busy = new Book(); busy.setId(2L); busy.setTitle("B");
            busy.setStatus(Book.BookStatus.BUSY);

            User reader = new User(); reader.setUsername("reader");
            Booking booking = new Booking();
            booking.setRequester(reader);
            booking.setRequestedAt(LocalDateTime.of(2024, 1, 10, 12, 0));
            booking.setBookedUntil(LocalDateTime.of(2024, 2, 10, 12, 0));

            when(userService.findByUsername("alice")).thenReturn(user);
            when(bookRepository.findByOwner(user)).thenReturn(List.of(busy));
            when(bookingRepository.findActiveBookingForBook(eq(busy), any()))
                    .thenReturn(Optional.of(booking));

            ResponseEntity<List<Map<String, Object>>> resp = statsController.getBooksGiven("alice");

            Map<String, Object> row = resp.getBody().get(0);
            assertThat(row).containsKey("bookedBy");
            assertThat(row.get("bookedBy")).isEqualTo("@reader");
        }

        @Test
        @DisplayName("Пользователь не найден — 404")
        void notFound_404() {
            when(userService.findByUsername("ghost")).thenThrow(new RuntimeException());
            ResponseEntity<?> resp = statsController.getBooksGiven("ghost");
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─── getReviews ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getReviews")
    class GetReviews {

        @Test
        @DisplayName("Возвращает список отзывов")
        void returnsList() {
            Review r = new Review(); r.setId(1L); r.setRating(5);
            r.setComment("OK"); r.setCreatedAt(LocalDateTime.now());
            when(userService.findByUsername("alice")).thenReturn(user);
            when(reviewRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(r));

            ResponseEntity<List<Map<String, Object>>> resp = statsController.getReviews("alice");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("Все поля отзыва присутствуют в ответе")
        void allFieldsPresent() {
            Review r = new Review(); r.setId(7L); r.setRating(4);
            r.setComment("Great"); r.setCreatedAt(LocalDateTime.now());
            when(userService.findByUsername("alice")).thenReturn(user);
            when(reviewRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(r));

            Map<String, Object> row = statsController.getReviews("alice").getBody().get(0);

            assertThat(row).containsKeys("id", "rating", "comment", "date", "edited");
            assertThat(row.get("rating")).isEqualTo(4);
        }

        @Test
        @DisplayName("updatedAt != null — edited=true")
        void updatedAt_editedTrue() {
            Review r = new Review(); r.setId(1L); r.setRating(3);
            r.setComment("Changed"); r.setCreatedAt(LocalDateTime.now());
            r.setUpdatedAt(LocalDateTime.now());
            when(userService.findByUsername("alice")).thenReturn(user);
            when(reviewRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(r));

            Map<String, Object> row = statsController.getReviews("alice").getBody().get(0);

            assertThat(row.get("edited")).isEqualTo(true);
        }

        @Test
        @DisplayName("updatedAt = null — edited=false")
        void updatedAtNull_editedFalse() {
            Review r = new Review(); r.setId(1L); r.setRating(5);
            r.setComment("New"); r.setCreatedAt(LocalDateTime.now());
            r.setUpdatedAt(null);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(reviewRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(r));

            Map<String, Object> row = statsController.getReviews("alice").getBody().get(0);

            assertThat(row.get("edited")).isEqualTo(false);
        }

        @Test
        @DisplayName("Пользователь не найден — 404")
        void notFound_404() {
            when(userService.findByUsername("ghost")).thenThrow(new RuntimeException());
            ResponseEntity<?> resp = statsController.getReviews("ghost");
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Отзыв без книги — bookTitle='---', bookImage=''")
        void reviewWithoutBook_defaultFields() {
            Review r = new Review(); r.setId(1L); r.setRating(3);
            r.setComment("No book"); r.setCreatedAt(LocalDateTime.now());
            r.setBook(null);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(reviewRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(r));

            Map<String, Object> row = statsController.getReviews("alice").getBody().get(0);

            assertThat(row.get("bookTitle")).isEqualTo("—");
            assertThat(row.get("bookImage")).isEqualTo("");
            assertThat(row.get("bookId")).isNull();
        }
    }

    // ─── getTimeline ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTimeline")
    class GetTimeline {

        @Test
        @DisplayName("Пользователь найден — 200 с registeredAt и daysInSystem")
        void found_200() {
            user.setRegisteredAt(LocalDateTime.of(2023, 6, 1, 0, 0));
            AchievementService.UserStats stats = new AchievementService.UserStats();
            stats.setDaysInSystem(300);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementService.calculateStats(user)).thenReturn(stats);

            ResponseEntity<Map<String, Object>> resp = statsController.getTimeline("alice");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).containsKeys("registeredAt", "daysInSystem");
            assertThat(resp.getBody().get("daysInSystem")).isEqualTo(300L);
        }

        @Test
        @DisplayName("registeredAt=null — возвращает '---'")
        void registeredAtNull_returnsDash() {
            user.setRegisteredAt(null);
            AchievementService.UserStats stats = new AchievementService.UserStats();
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementService.calculateStats(user)).thenReturn(stats);

            ResponseEntity<Map<String, Object>> resp = statsController.getTimeline("alice");

            assertThat(resp.getBody().get("registeredAt")).isEqualTo("—");
        }

        @Test
        @DisplayName("registeredAt форматируется как dd.MM.yyyy")
        void registeredAtFormattedCorrectly() {
            user.setRegisteredAt(LocalDateTime.of(2023, 3, 15, 10, 30));
            AchievementService.UserStats stats = new AchievementService.UserStats();
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementService.calculateStats(user)).thenReturn(stats);

            ResponseEntity<Map<String, Object>> resp = statsController.getTimeline("alice");

            assertThat(resp.getBody().get("registeredAt")).isEqualTo("15.03.2023");
        }

        @Test
        @DisplayName("Пользователь не найден — 404")
        void notFound_404() {
            when(userService.findByUsername("ghost")).thenThrow(new RuntimeException());
            ResponseEntity<?> resp = statsController.getTimeline("ghost");
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─── toggleFeatured ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleFeatured")
    class ToggleFeatured {

        @Test
        @DisplayName("Достижение не существует — 404")
        void achievementNotFound_404() {
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementRepository.findByCode("UNKNOWN")).thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> resp =
                    statsController.toggleFeatured("UNKNOWN", principal);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("Достижение не получено — 400 с ошибкой")
        void notEarned_400() {
            Achievement a = makeAchievement("FIRST_BOOK");
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementRepository.findByCode("FIRST_BOOK")).thenReturn(Optional.of(a));
            when(userAchievementRepository.findByUserAndAchievement(user, a))
                    .thenReturn(Optional.empty());

            ResponseEntity<Map<String, Object>> resp =
                    statsController.toggleFeatured("FIRST_BOOK", principal);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(resp.getBody()).containsKey("error");
        }

        @Test
        @DisplayName("Добавление на витрину — featured=true, счётчик увеличен")
        void addToFeatured_success() {
            Achievement a = makeAchievement("FIRST_BOOK");
            UserAchievement ua = makeUserAchievement(a, false);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementRepository.findByCode("FIRST_BOOK")).thenReturn(Optional.of(a));
            when(userAchievementRepository.findByUserAndAchievement(user, a))
                    .thenReturn(Optional.of(ua));
            when(userAchievementRepository.countByUserAndFeaturedTrue(user)).thenReturn(0L);
            when(userAchievementRepository.save(ua)).thenReturn(ua);
            // После сохранения — featured=true, count=1
            when(userAchievementRepository.countByUserAndFeaturedTrue(user))
                    .thenReturn(0L)   // первый вызов (проверка лимита)
                    .thenReturn(1L);  // второй вызов (ответ)

            ResponseEntity<Map<String, Object>> resp =
                    statsController.toggleFeatured("FIRST_BOOK", principal);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(ua.isFeatured()).isTrue();
            verify(userAchievementRepository).save(ua);
        }

        @Test
        @DisplayName("Снятие с витрины — featured=false")
        void removeFromFeatured_success() {
            Achievement a = makeAchievement("FIRST_BOOK");
            UserAchievement ua = makeUserAchievement(a, true); // уже на витрине
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementRepository.findByCode("FIRST_BOOK")).thenReturn(Optional.of(a));
            when(userAchievementRepository.findByUserAndAchievement(user, a))
                    .thenReturn(Optional.of(ua));
            when(userAchievementRepository.save(ua)).thenReturn(ua);
            when(userAchievementRepository.countByUserAndFeaturedTrue(user)).thenReturn(0L);

            ResponseEntity<Map<String, Object>> resp =
                    statsController.toggleFeatured("FIRST_BOOK", principal);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(ua.isFeatured()).isFalse();
        }

        @Test
        @DisplayName("Превышен лимит 3 витринных достижения — 403")
        void maxFeaturedExceeded_403() {
            Achievement a = makeAchievement("BOOKWORM_5");
            UserAchievement ua = makeUserAchievement(a, false);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(achievementRepository.findByCode("BOOKWORM_5")).thenReturn(Optional.of(a));
            when(userAchievementRepository.findByUserAndAchievement(user, a))
                    .thenReturn(Optional.of(ua));
            when(userAchievementRepository.countByUserAndFeaturedTrue(user)).thenReturn(3L);

            ResponseEntity<Map<String, Object>> resp =
                    statsController.toggleFeatured("BOOKWORM_5", principal);

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(resp.getBody()).containsKey("error");
            verify(userAchievementRepository, never()).save(any());
        }
    }

    // ─── getFeaturedAchievements ──────────────────────────────────────────────

    @Nested
    @DisplayName("getFeaturedAchievements")
    class GetFeaturedAchievements {

        @Test
        @DisplayName("Пользователь найден — 200 со списком витринных достижений")
        void found_200() {
            Achievement a = makeAchievement("FIRST_BOOK");
            UserAchievement ua = makeUserAchievement(a, true);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(userAchievementRepository
                    .findByUserAndFeaturedTrueOrderByEarnedAtDesc(user))
                    .thenReturn(List.of(ua));

            ResponseEntity<List<Map<String, Object>>> resp =
                    statsController.getFeaturedAchievements("alice");

            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(resp.getBody()).hasSize(1);
        }

        @Test
        @DisplayName("Нет витринных достижений — пустой список")
        void noFeatured_emptyList() {
            when(userService.findByUsername("alice")).thenReturn(user);
            when(userAchievementRepository
                    .findByUserAndFeaturedTrueOrderByEarnedAtDesc(user))
                    .thenReturn(List.of());

            ResponseEntity<List<Map<String, Object>>> resp =
                    statsController.getFeaturedAchievements("alice");

            assertThat(resp.getBody()).isEmpty();
        }

        @Test
        @DisplayName("Все поля достижения присутствуют в ответе")
        void allFieldsPresent() {
            Achievement a = makeAchievement("FIRST_BOOK");
            a.setTitle("Первопроходец");
            a.setIcon("📚");
            a.setDescription("Добавил первую книгу");
            UserAchievement ua = makeUserAchievement(a, true);
            when(userService.findByUsername("alice")).thenReturn(user);
            when(userAchievementRepository
                    .findByUserAndFeaturedTrueOrderByEarnedAtDesc(user))
                    .thenReturn(List.of(ua));

            Map<String, Object> row =
                    statsController.getFeaturedAchievements("alice").getBody().get(0);

            assertThat(row).containsKeys("code", "title", "icon", "description");
            assertThat(row.get("title")).isEqualTo("Первопроходец");
            assertThat(row.get("icon")).isEqualTo("📚");
        }

        @Test
        @DisplayName("Пользователь не найден — 404")
        void notFound_404() {
            when(userService.findByUsername("ghost")).thenThrow(new RuntimeException());
            ResponseEntity<?> resp = statsController.getFeaturedAchievements("ghost");
            assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Achievement makeAchievement(String code) {
        Achievement a = new Achievement();
        a.setId(1L);
        a.setCode(code);
        a.setTitle("Title " + code);
        a.setDescription("Desc");
        a.setIcon("⭐");
        a.setType(Achievement.AchievementType.BOOKS_ADDED);
        a.setConditionValue(1);
        return a;
    }

    private UserAchievement makeUserAchievement(Achievement a, boolean featured) {
        UserAchievement ua = new UserAchievement();
        ua.setId(1L);
        ua.setUser(user);
        ua.setAchievement(a);
        ua.setEarnedAt(LocalDateTime.now());
        ua.setFeatured(featured);
        return ua;
    }
}