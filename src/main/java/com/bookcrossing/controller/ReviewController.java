package com.bookcrossing.controller;

import com.bookcrossing.model.Book;
import com.bookcrossing.model.Review;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.ReviewRepository;
import com.bookcrossing.service.AchievementService;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewRepository    reviewRepository;
    private final BookRepository      bookRepository;
    private final UserService         userService;
    private final NotificationService notificationService;
    private final AchievementService  achievementService;

    public ReviewController(ReviewRepository reviewRepository,
                            BookRepository bookRepository,
                            UserService userService,
                            NotificationService notificationService,
                            AchievementService achievementService) {
        this.reviewRepository    = reviewRepository;
        this.bookRepository      = bookRepository;
        this.userService         = userService;
        this.notificationService = notificationService;
        this.achievementService  = achievementService;
    }

    // ── Форма добавления / редактирования ─────────────────────

    @GetMapping("/add")
    public String showForm(@RequestParam Long bookId, Principal principal, Model model) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Книга не найдена: " + bookId));
        User reviewer = userService.findByUsername(principal.getName());

        // Нельзя оставить отзыв на свою книгу
        if (book.getOwner().getId().equals(reviewer.getId())) {
            model.addAttribute("book", book);
            model.addAttribute("error", "Нельзя оставлять отзыв на собственную книгу.");
            return "review_add";
        }

        // Проверяем — есть ли уже отзыв этого пользователя
        Optional<Review> existing = reviewRepository.findByBookAndUser(book, reviewer);
        if (existing.isPresent()) {
            // Передаём в форму для редактирования
            model.addAttribute("book",           book);
            model.addAttribute("existingReview", existing.get());
            model.addAttribute("editMode",       true);
            return "review_add";
        }

        model.addAttribute("book", book);
        return "review_add";
    }

    // ── Сохранение / Обновление ───────────────────────────────

    @Transactional
    @PostMapping("/add")
    public String saveReview(@RequestParam Long bookId,
                             @RequestParam int rating,
                             @RequestParam String comment,
                             @RequestParam(required = false) Long existingReviewId,
                             Principal principal,
                             RedirectAttributes ra,
                             Model model) {

        User reviewer = userService.findByUsername(principal.getName());
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Книга не найдена: " + bookId));

        // Нельзя оставить отзыв на свою книгу
        if (book.getOwner().getId().equals(reviewer.getId())) {
            model.addAttribute("book",  book);
            model.addAttribute("error", "Нельзя оставлять отзыв на собственную книгу.");
            return "review_add";
        }

        // Валидация рейтинга
        if (rating < 1 || rating > 5) {
            model.addAttribute("book",  book);
            model.addAttribute("error", "Выберите оценку от 1 до 5.");
            return "review_add";
        }

        // Валидация комментария
        String trimmedComment = comment != null ? comment.trim() : "";
        if (trimmedComment.length() < 10) {
            model.addAttribute("book",  book);
            model.addAttribute("error", "Комментарий должен содержать минимум 10 символов.");
            return "review_add";
        }
        if (trimmedComment.length() > 2000) {
            model.addAttribute("book",  book);
            model.addAttribute("error", "Комментарий не должен превышать 2000 символов.");
            return "review_add";
        }

        Review review;
        boolean isEdit = false;

        // Если передан id существующего отзыва — редактируем
        if (existingReviewId != null) {
            Optional<Review> opt = reviewRepository.findById(existingReviewId);
            if (opt.isPresent() && opt.get().getUser().getId().equals(reviewer.getId())) {
                review = opt.get();
                isEdit = true;
            } else {
                review = new Review(); // защита от подделки
            }
        } else {
            // Проверяем нет ли уже отзыва (дополнительная защита)
            Optional<Review> existing = reviewRepository.findByBookAndUser(book, reviewer);
            if (existing.isPresent()) {
                review = existing.get();
                isEdit = true;
            } else {
                review = new Review();
                review.setUser(reviewer);
                review.setBook(book);
                review.setTargetUser(book.getOwner());
                review.setCreatedAt(LocalDateTime.now());
            }
        }

        review.setRating(rating);
        review.setComment(trimmedComment);
        if (isEdit) review.setUpdatedAt(LocalDateTime.now());
        reviewRepository.save(review);

        // Уведомление владельцу (только при новом отзыве)
        if (!isEdit && !book.getOwner().getUsername().equals(reviewer.getUsername())) {
            String stars = "★".repeat(rating) + "☆".repeat(5 - rating);
            notificationService.sendNotification(
                    book.getOwner().getUsername(),
                    "Новый отзыв на «" + book.getTitle() + "»",
                    "@" + reviewer.getUsername() + " оценил(а): " + stars + " — " +
                            trimmedComment.substring(0, Math.min(80, trimmedComment.length())) + "…",
                    "/"
            );
        }

        // Проверяем достижения
        achievementService.checkAndAward(reviewer);

        ra.addFlashAttribute("success",
                isEdit ? "Отзыв обновлён!" : "Отзыв опубликован!");
        return "redirect:/";
    }

    // ── Удалить свой отзыв ─────────────────────────────────────

    @Transactional
    @PostMapping("/{id}/delete")
    public String deleteReview(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        Review review = reviewRepository.findById(id).orElse(null);
        if (review == null || !review.getUser().getUsername().equals(principal.getName())) {
            ra.addFlashAttribute("error", "Отзыв не найден.");
            return "redirect:/";
        }
        reviewRepository.delete(review);
        ra.addFlashAttribute("success", "Отзыв удалён.");
        return "redirect:/";
    }

    // ── API: мои отзывы для статистики профиля ────────────────

    @GetMapping("/my")
    @ResponseBody
    public java.util.List<Review> getMyReviews(Principal principal) {
        User user = userService.findByUsername(principal.getName());
        return reviewRepository.findByUserOrderByCreatedAtDesc(user);
    }
}