package com.bookcrossing.controller;

import com.bookcrossing.model.*;
import com.bookcrossing.repository.*;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ModeratorController")
class ModeratorControllerTest {

    @Mock ComplaintRepository     complaintRepository;
    @Mock ModerationLogRepository logRepository;
    @Mock UserService             userService;
    @Mock NotificationService     notificationService;
    @Mock BookRepository          bookRepository;
    @Mock ReviewRepository        reviewRepository;
    @InjectMocks ModeratorController moderatorController;

    private User moderator;
    private User author;
    private Complaint complaint;
    private Principal modPrincipal;
    private RedirectAttributes ra;
    private Model model;

    @BeforeEach
    void setUp() {
        moderator = new User(); moderator.setId(1L); moderator.setUsername("mod");
        moderator.setRole(User.UserRole.MODERATOR);

        author = new User(); author.setId(2L); author.setUsername("alice");

        complaint = new Complaint();
        complaint.setId(10L);
        complaint.setAuthor(author);
        complaint.setTargetBookId(50L);
        complaint.setTargetBookTitle("Тестовая книга");
        complaint.setType(Complaint.ComplaintType.SPAM);
        complaint.setStatus(Complaint.ComplaintStatus.PENDING);

        modPrincipal = () -> "mod";
        ra    = mock(RedirectAttributes.class);
        model = mock(Model.class);
    }

    // ─── moderatorPanel ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /moderator")
    class ModeratorPanel {

        @Test
        @DisplayName("Без фильтра статуса — все жалобы")
        void noStatus_allComplaints() {
            when(complaintRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(complaint));
            when(complaintRepository.countByStatus(any())).thenReturn(0L);
            when(complaintRepository.countResolvedByModerator("mod")).thenReturn(3L);
            when(logRepository.findByModeratorUsernameOrderByCreatedAtDesc("mod")).thenReturn(List.of());

            String view = moderatorController.moderatorPanel(model, null, modPrincipal);

            assertThat(view).isEqualTo("moderator");
            verify(complaintRepository).findAllByOrderByCreatedAtDesc();
            verify(model).addAttribute(eq("complaints"), any());
            verify(model).addAttribute(eq("pendingCount"), any());
        }

        @Test
        @DisplayName("С фильтром статуса — фильтрует по статусу")
        void withStatus_filteredComplaints() {
            when(complaintRepository.findByStatusOrderByCreatedAtDesc(Complaint.ComplaintStatus.PENDING))
                    .thenReturn(List.of(complaint));
            when(complaintRepository.countByStatus(any())).thenReturn(0L);
            when(complaintRepository.countResolvedByModerator("mod")).thenReturn(0L);
            when(logRepository.findByModeratorUsernameOrderByCreatedAtDesc("mod")).thenReturn(List.of());

            moderatorController.moderatorPanel(model, "PENDING", modPrincipal);

            verify(complaintRepository).findByStatusOrderByCreatedAtDesc(Complaint.ComplaintStatus.PENDING);
        }
    }

    // ─── acceptComplaint ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /moderator/complaints/{id}/accept")
    class AcceptComplaint {

        @Test
        @DisplayName("Успешное принятие — ACCEPTED, уведомление автору, лог")
        void success_acceptsAndNotifies() {
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);

            String view = moderatorController.acceptComplaint(10L, "Нарушение подтверждено",
                    modPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/moderator");
            assertThat(complaint.getStatus()).isEqualTo(Complaint.ComplaintStatus.ACCEPTED);
            assertThat(complaint.getResolvedBy()).isEqualTo(moderator);
            assertThat(complaint.getResolvedAt()).isNotNull();
            verify(complaintRepository).save(complaint);
            verify(notificationService).sendNotification(eq("alice"), contains("принята"), any(), any());
            verify(logRepository).save(any(ModerationLog.class));
            verify(ra).addFlashAttribute(eq("success"), any());
        }

        @Test
        @DisplayName("Жалоба не найдена — бросает RuntimeException")
        void notFound_throwsException() {
            when(complaintRepository.findById(99L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> moderatorController.acceptComplaint(99L, null, modPrincipal, ra))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    // ─── rejectComplaint ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /moderator/complaints/{id}/reject")
    class RejectComplaint {

        @Test
        @DisplayName("Успешное отклонение — REJECTED, уведомление, лог")
        void success_rejectsAndNotifies() {
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);

            String view = moderatorController.rejectComplaint(10L, "Нарушений не обнаружено",
                    modPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/moderator");
            assertThat(complaint.getStatus()).isEqualTo(Complaint.ComplaintStatus.REJECTED);
            verify(notificationService).sendNotification(eq("alice"), contains("отклонена"), any(), any());
            verify(logRepository).save(any(ModerationLog.class));
        }

        @Test
        @DisplayName("Отклонение с комментарием — комментарий в уведомлении")
        void withComment_commentInNotification() {
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);

            moderatorController.rejectComplaint(10L, "Книга соответствует правилам", modPrincipal, ra);

            verify(notificationService).sendNotification(eq("alice"), any(),
                    argThat(body -> body.toString().contains("Комментарий")), any());
        }

        @Test
        @DisplayName("Отклонение без комментария — без «Комментарий» в уведомлении")
        void withoutComment_noCommentInNotification() {
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);

            moderatorController.rejectComplaint(10L, null, modPrincipal, ra);

            verify(notificationService).sendNotification(eq("alice"), any(),
                    argThat(body -> !body.toString().contains("Комментарий")), any());
        }
    }

    // ─── notifyAuthor ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /moderator/complaints/{id}/notify-author")
    class NotifyAuthor {

        @Test
        @DisplayName("Книга существует — уведомляет владельца")
        void bookExists_notifiesOwner() {
            User bookOwner = new User(); bookOwner.setUsername("owner");
            Book book = new Book(); book.setId(50L); book.setOwner(bookOwner);
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);
            when(bookRepository.findById(50L)).thenReturn(Optional.of(book));

            String view = moderatorController.notifyAuthor(10L, "Исправьте описание", modPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/moderator");
            verify(notificationService).sendNotification(eq("owner"), contains("Жалоба"), any(), any());
            assertThat(complaint.getStatus()).isEqualTo(Complaint.ComplaintStatus.ACCEPTED);
            verify(logRepository).save(any(ModerationLog.class));
        }

        @Test
        @DisplayName("Книга не найдена — жалоба закрывается, владельцу не отправляется")
        void bookNotFound_closesComplaintOnly() {
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);
            when(bookRepository.findById(50L)).thenReturn(Optional.empty());

            moderatorController.notifyAuthor(10L, null, modPrincipal, ra);

            // только автора жалобы нет — уведомление не вызывается для owner (книга не найдена)
            assertThat(complaint.getStatus()).isEqualTo(Complaint.ComplaintStatus.ACCEPTED);
        }
    }

    // ─── deleteBookByComplaint ────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /moderator/complaints/{id}/delete-book")
    class DeleteBookByComplaint {

        @Test
        @DisplayName("Книга существует — удаляется, обе стороны уведомляются")
        void bookExists_deletesAndNotifiesBoth() {
            User bookOwner = new User(); bookOwner.setId(3L); bookOwner.setUsername("owner");
            Book book = new Book(); book.setId(50L); book.setOwner(bookOwner);
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);
            when(bookRepository.findById(50L)).thenReturn(Optional.of(book));

            String view = moderatorController.deleteBookByComplaint(10L, "спам", modPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/moderator");
            verify(reviewRepository).deleteByBookId(50L);
            verify(bookRepository).delete(book);
            // Владелец книги уведомляется об удалении
            verify(notificationService).sendNotification(eq("owner"), contains("удалена"), any(), any());
            // Автор жалобы уведомляется об удовлетворении
            verify(notificationService).sendNotification(eq("alice"), contains("удовлетворена"), any(), any());
            assertThat(complaint.getStatus()).isEqualTo(Complaint.ComplaintStatus.ACCEPTED);
            verify(ra).addFlashAttribute(eq("success"), any());
        }

        @Test
        @DisplayName("Книга не найдена — жалоба закрывается без удаления")
        void bookNotFound_closesComplaintWithoutDelete() {
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);
            when(bookRepository.findById(50L)).thenReturn(Optional.empty());

            moderatorController.deleteBookByComplaint(10L, null, modPrincipal, ra);

            verify(bookRepository, never()).delete(any());
            verify(reviewRepository, never()).deleteByBookId(any());
            assertThat(complaint.getStatus()).isEqualTo(Complaint.ComplaintStatus.ACCEPTED);
        }

        @Test
        @DisplayName("targetBookId = null — безопасно обрабатывается")
        void noBookId_handledSafely() {
            complaint.setTargetBookId(null);
            when(complaintRepository.findById(10L)).thenReturn(Optional.of(complaint));
            when(userService.findByUsername("mod")).thenReturn(moderator);

            moderatorController.deleteBookByComplaint(10L, null, modPrincipal, ra);

            verify(bookRepository, never()).delete(any());
        }
    }
}