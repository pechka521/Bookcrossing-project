package com.bookcrossing.controller;

import com.bookcrossing.model.Book;
import com.bookcrossing.model.Booking;
import com.bookcrossing.model.Booking.BookingStatus;
import com.bookcrossing.model.Review;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.AchievementRepository;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.BookingRepository;
import com.bookcrossing.repository.ReviewRepository;
import com.bookcrossing.repository.UserAchievementRepository;
import com.bookcrossing.service.AchievementService;
import com.bookcrossing.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api")
public class StatsController {

    private final UserService               userService;
    private final AchievementService        achievementService;
    private final BookRepository            bookRepository;
    private final ReviewRepository          reviewRepository;
    private final BookingRepository         bookingRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final AchievementRepository     achievementRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public StatsController(UserService userService,
                           AchievementService achievementService,
                           BookRepository bookRepository,
                           ReviewRepository reviewRepository,
                           BookingRepository bookingRepository,
                           UserAchievementRepository userAchievementRepository,
                           AchievementRepository achievementRepository) {
        this.userService               = userService;
        this.achievementService        = achievementService;
        this.bookRepository            = bookRepository;
        this.reviewRepository          = reviewRepository;
        this.bookingRepository         = bookingRepository;
        this.userAchievementRepository = userAchievementRepository;
        this.achievementRepository     = achievementRepository;
    }

    /** Статистика текущего пользователя */
    @GetMapping("/users/me/stats")
    public ResponseEntity<AchievementService.UserStats> getMyStats(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        achievementService.checkAndAward(user);
        return ResponseEntity.ok(achievementService.calculateStats(user));
    }

    /** Статистика по username (публичная) */
    @GetMapping("/users/{username}/stats")
    public ResponseEntity<?> getUserStats(@PathVariable String username) {
        try {
            User user = userService.findByUsername(username);
            return ResponseEntity.ok(achievementService.calculateStats(user));
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Все достижения с флагом earned и featured */
    @GetMapping("/achievements")
    public ResponseEntity<List<AchievementService.AchievementDto>> getAchievements(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return ResponseEntity.ok(achievementService.getUserAchievements(user));
    }

    /** Детализация: добавленные книги */
    @GetMapping("/users/{username}/books-added")
    public ResponseEntity<List<Map<String, Object>>> getBooksAdded(@PathVariable String username) {
        try {
            User user = userService.findByUsername(username);
            List<Book> books = bookRepository.findByOwner(user);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Book b : books) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",     b.getId());
                m.put("title",  b.getTitle());
                m.put("author", b.getAuthor());
                // genre is stored as String (not enum) in this project
                m.put("genre",  b.getGenre() != null ? b.getGenre().toString() : "—");
                m.put("status", b.getStatus().getDisplayValue());
                m.put("image",  b.getImageDisplay());
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Детализация: переданные книги (статус BUSY) */
    @GetMapping("/users/{username}/books-given")
    public ResponseEntity<List<Map<String, Object>>> getBooksGiven(@PathVariable String username) {
        try {
            User user = userService.findByUsername(username);
            List<Book> given = bookRepository.findByOwner(user).stream()
                    .filter(b -> b.getStatus() == Book.BookStatus.BUSY)
                    .toList();
            List<Map<String, Object>> result = new ArrayList<>();
            for (Book b : given) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",     b.getId());
                m.put("title",  b.getTitle());
                m.put("author", b.getAuthor());
                m.put("genre",  b.getGenre() != null ? b.getGenre().toString() : "—");
                m.put("image",  b.getImageDisplay());
                bookingRepository.findActiveBookingForBook(b, BookingStatus.ACCEPTED).ifPresent(booking -> {
                    m.put("bookedBy",    "@" + booking.getRequester().getUsername());
                    m.put("bookedSince", booking.getRequestedAt().format(FMT));
                    m.put("bookedUntil", booking.getBookedUntil() != null
                            ? booking.getBookedUntil().format(FMT) : "не указано");
                });
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Детализация: написанные отзывы */
    @GetMapping("/users/{username}/reviews")
    public ResponseEntity<List<Map<String, Object>>> getReviews(@PathVariable String username) {
        try {
            User user = userService.findByUsername(username);
            List<Review> reviews = reviewRepository.findByUserOrderByCreatedAtDesc(user);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Review r : reviews) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id",        r.getId());
                m.put("bookTitle", r.getBook() != null ? r.getBook().getTitle() : "—");
                m.put("bookImage", r.getBook() != null ? r.getBook().getImageDisplay() : "");
                m.put("bookId",    r.getBook() != null ? r.getBook().getId() : null);
                m.put("rating",    r.getRating());
                m.put("comment",   r.getComment());
                m.put("date",      r.getCreatedAt() != null ? r.getCreatedAt().format(FMT) : "");
                m.put("edited",    r.getUpdatedAt() != null);
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Детализация: дни в системе */
    @GetMapping("/users/{username}/timeline")
    public ResponseEntity<Map<String, Object>> getTimeline(@PathVariable String username) {
        try {
            User user = userService.findByUsername(username);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("registeredAt", user.getRegisteredAt() != null
                    ? user.getRegisteredAt().format(FMT) : "—");
            m.put("daysInSystem", achievementService.calculateStats(user).getDaysInSystem());
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Витрина достижений (публичная) */
    @GetMapping("/users/{username}/featured-achievements")
    public ResponseEntity<List<Map<String, Object>>> getFeaturedAchievements(
            @PathVariable String username) {
        try {
            User user = userService.findByUsername(username);
            var list = userAchievementRepository.findByUserAndFeaturedTrueOrderByEarnedAtDesc(user);
            List<Map<String, Object>> result = new ArrayList<>();
            for (var ua : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code",        ua.getAchievement().getCode());
                m.put("title",       ua.getAchievement().getTitle());
                m.put("icon",        ua.getAchievement().getIcon());
                m.put("description", ua.getAchievement().getDescription());
                result.add(m);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Переключить витрину достижения (добавить/убрать) */
    @Transactional
    @PostMapping("/achievements/{code}/toggle-featured")
    public ResponseEntity<Map<String, Object>> toggleFeatured(
            @PathVariable String code, Principal principal) {
        try {
            User user = userService.findByUsername(principal.getName());
            var achievement = achievementRepository.findByCode(code).orElse(null);
            if (achievement == null) return ResponseEntity.notFound().build();

            var ua = userAchievementRepository
                    .findByUserAndAchievement(user, achievement).orElse(null);
            if (ua == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Достижение ещё не получено"));
            }

            if (!ua.isFeatured()) {
                long count = userAchievementRepository.countByUserAndFeaturedTrue(user);
                if (count >= 3) {
                    return ResponseEntity.status(403)
                            .body(Map.of("error", "Максимум 3 достижения на витрине"));
                }
                ua.setFeatured(true);
            } else {
                ua.setFeatured(false);
            }
            userAchievementRepository.save(ua);

            return ResponseEntity.ok(Map.of(
                    "featured",      ua.isFeatured(),
                    "featuredCount", userAchievementRepository.countByUserAndFeaturedTrue(user)
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}