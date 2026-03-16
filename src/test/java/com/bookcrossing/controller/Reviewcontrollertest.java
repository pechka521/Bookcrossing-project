package com.bookcrossing.controller;

import com.bookcrossing.model.Book;
import com.bookcrossing.model.Review;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.ReviewRepository;
import com.bookcrossing.service.AchievementService;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReviewController")
class ReviewControllerTest {

    @Mock ReviewRepository   reviewRepository;
    @Mock BookRepository     bookRepository;
    @Mock UserService        userService;
    @Mock NotificationService notificationService;
    @Mock AchievementService achievementService;
    @InjectMocks ReviewController reviewController;

    private User owner;
    private User reviewer;
    private Book book;
    private Principal reviewerPrincipal;
    private Model model;
    private RedirectAttributes ra;

    @BeforeEach
    void setUp() {
        owner    = new User(); owner.setId(1L);    owner.setUsername("owner");
        reviewer = new User(); reviewer.setId(2L); reviewer.setUsername("alice");
        book     = new Book(); book.setId(10L);    book.setTitle("Книга");
        book.setOwner(owner);

        reviewerPrincipal = () -> "alice";
        model = mock(Model.class);
        ra    = mock(RedirectAttributes.class);
    }

    // ─── GET /reviews/add ────────────────────────────────────────────────────

    @Nested
    @DisplayName("showForm GET")
    class ShowForm {

        @Test
        @DisplayName("Своя книга — ошибка в модели")
        void ownBook_returnsError() {
            book.setOwner(reviewer);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(userService.findByUsername("alice")).thenReturn(reviewer);

            String view = reviewController.showForm(10L, reviewerPrincipal, model);

            assertThat(view).isEqualTo("review_add");
            verify(model).addAttribute(eq("error"), any());
        }

        @Test
        @DisplayName("Уже есть отзыв — передаёт existingReview и editMode=true")
        void existingReview_passesEditMode() {
            Review existing = new Review();
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(reviewRepository.findByBookAndUser(book, reviewer)).thenReturn(Optional.of(existing));

            String view = reviewController.showForm(10L, reviewerPrincipal, model);

            assertThat(view).isEqualTo("review_add");
            verify(model).addAttribute(eq("existingReview"), eq(existing));
            verify(model).addAttribute(eq("editMode"), eq(true));
        }

        @Test
        @DisplayName("Новый отзыв — просто возвращает форму")
        void newReview_returnsForm() {
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(reviewRepository.findByBookAndUser(book, reviewer)).thenReturn(Optional.empty());

            String view = reviewController.showForm(10L, reviewerPrincipal, model);

            assertThat(view).isEqualTo("review_add");
        }
    }

    // ─── POST /reviews/add ───────────────────────────────────────────────────

    @Nested
    @DisplayName("saveReview POST")
    class SaveReview {

        @Test
        @DisplayName("Своя книга — ошибка")
        void ownBook_returnsError() {
            book.setOwner(reviewer);
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

            String view = reviewController.saveReview(10L, 5, "Отличная книга!", null,
                    reviewerPrincipal, ra, model);

            assertThat(view).isEqualTo("review_add");
            verify(model).addAttribute(eq("error"), any());
        }

        @Test
        @DisplayName("Рейтинг < 1 — ошибка")
        void ratingTooLow_returnsError() {
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

            String view = reviewController.saveReview(10L, 0, "Нормальный комментарий здесь", null,
                    reviewerPrincipal, ra, model);

            assertThat(view).isEqualTo("review_add");
            verify(model).addAttribute(eq("error"), contains("1 до 5"));
        }

        @Test
        @DisplayName("Рейтинг > 5 — ошибка")
        void ratingTooHigh_returnsError() {
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

            String view = reviewController.saveReview(10L, 6, "Нормальный комментарий здесь", null,
                    reviewerPrincipal, ra, model);

            assertThat(view).isEqualTo("review_add");
        }

        @Test
        @DisplayName("Комментарий меньше 10 символов — ошибка")
        void commentTooShort_returnsError() {
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

            String view = reviewController.saveReview(10L, 4, "Кратко", null,
                    reviewerPrincipal, ra, model);

            assertThat(view).isEqualTo("review_add");
            verify(model).addAttribute(eq("error"), contains("10"));
        }

        @Test
        @DisplayName("Комментарий > 2000 символов — ошибка")
        void commentTooLong_returnsError() {
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

            String longComment = "а".repeat(2001);
            String view = reviewController.saveReview(10L, 4, longComment, null,
                    reviewerPrincipal, ra, model);

            assertThat(view).isEqualTo("review_add");
            verify(model).addAttribute(eq("error"), contains("2000"));
        }

        @Test
        @DisplayName("Успешное создание — сохраняет, уведомляет владельца, редирект")
        void newReview_savesAndNotifies() {
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(reviewRepository.findByBookAndUser(book, reviewer)).thenReturn(Optional.empty());

            String view = reviewController.saveReview(10L, 5, "Очень понравилась книга, рекомендую!", null,
                    reviewerPrincipal, ra, model);

            assertThat(view).isEqualTo("redirect:/");
            verify(reviewRepository).save(any(Review.class));
            verify(notificationService).sendNotification(eq("owner"), any(), any(), any());
            verify(achievementService).checkAndAward(reviewer);
        }

        @Test
        @DisplayName("Редактирование существующего отзыва — обновляет, не отправляет уведомление")
        void editReview_updatesWithoutNotification() {
            Review existing = new Review();
            existing.setId(5L);
            existing.setUser(reviewer);
            existing.setBook(book);
            existing.setCreatedAt(LocalDateTime.now().minusDays(1));
            when(userService.findByUsername("alice")).thenReturn(reviewer);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(reviewRepository.findById(5L)).thenReturn(Optional.of(existing));

            String view = reviewController.saveReview(10L, 3, "Изменённый комментарий нормальной длины", 5L,
                    reviewerPrincipal, ra, model);

            assertThat(view).isEqualTo("redirect:/");
            verify(reviewRepository).save(existing);
            verify(notificationService, never()).sendNotification(any(), any(), any(), any());
        }
        @Test
        @DisplayName("Своя книга — уведомление не отправляется (ownBook check первым)")
        void sameUser_noNotification() {
            User ownerAlsoReviewer = new User();
            ownerAlsoReviewer.setId(3L);
            ownerAlsoReviewer.setUsername("same");
            Book b2 = new Book(); b2.setId(20L); b2.setTitle("B2");
            b2.setOwner(ownerAlsoReviewer);

            when(userService.findByUsername("same")).thenReturn(ownerAlsoReviewer);
            when(bookRepository.findById(20L)).thenReturn(Optional.of(b2));

            Principal p = () -> "same";
            reviewController.saveReview(20L, 4, "Комментарий минимальной длины тут", null, p, ra, model);

            verify(model).addAttribute(eq("error"), any());
            verify(notificationService, never()).sendNotification(any(), any(), any(), any());
        }
    }

    // ─── POST /reviews/{id}/delete ────────────────────────────────────────────

    @Nested
    @DisplayName("deleteReview")
    class DeleteReview {

        @Test
        @DisplayName("Отзыв не найден — ошибка")
        void notFound_flashError() {
            when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

            String view = reviewController.deleteReview(99L, reviewerPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/");
            verify(ra).addFlashAttribute(eq("error"), any());
        }

        @Test
        @DisplayName("Чужой отзыв — ошибка")
        void wrongUser_flashError() {
            Review r = new Review(); r.setUser(owner); // owner, not reviewer
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));

            String view = reviewController.deleteReview(1L, reviewerPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/");
            verify(ra).addFlashAttribute(eq("error"), any());
            verify(reviewRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Успешное удаление своего отзыва")
        void success_deletesAndFlash() {
            Review r = new Review(); r.setUser(reviewer);
            when(reviewRepository.findById(1L)).thenReturn(Optional.of(r));

            String view = reviewController.deleteReview(1L, reviewerPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/");
            verify(reviewRepository).delete(r);
            verify(ra).addFlashAttribute(eq("success"), any());
        }
    }
}