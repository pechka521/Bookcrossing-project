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
import org.mockito.ArgumentCaptor;
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
@DisplayName("AdminController")
class AdminControllerTest {

    @Mock UserService             userService;
    @Mock BookRepository          bookRepository;
    @Mock ReviewRepository        reviewRepository;
    @Mock ModerationLogRepository logRepository;
    @Mock NotificationService     notificationService;
    @InjectMocks AdminController  adminController;

    private User admin;
    private User target;
    private Principal adminPrincipal;
    private RedirectAttributes ra;
    private Model model;

    @BeforeEach
    void setUp() {
        admin = new User(); admin.setId(1L); admin.setUsername("admin");
        admin.setRole(User.UserRole.ADMIN);

        target = new User(); target.setId(2L); target.setUsername("bob");
        target.setRole(User.UserRole.USER);

        adminPrincipal = () -> "admin";
        ra    = mock(RedirectAttributes.class);
        model = mock(Model.class);
    }

    // ─── adminPanel ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /admin")
    class AdminPanel {

        @Test
        @DisplayName("Без query — возвращает все книги и пользователей")
        void noQuery_loadAll() {
            when(bookRepository.findAll()).thenReturn(List.of());
            when(userService.searchUsers(null)).thenReturn(List.of());
            when(logRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            String view = adminController.adminPanel(model, null);

            assertThat(view).isEqualTo("admin");
            verify(bookRepository).findAll();
            verify(model).addAttribute(eq("users"), any());
            verify(model).addAttribute(eq("logs"), any());
            verify(model).addAttribute(eq("allRoles"), any());
        }

        @Test
        @DisplayName("С query — ищет по запросу")
        void withQuery_searchByQuery() {
            when(bookRepository.searchByQuery("war")).thenReturn(List.of());
            when(userService.searchUsers("war")).thenReturn(List.of());
            when(logRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            adminController.adminPanel(model, "war");

            verify(bookRepository).searchByQuery("war");
            verify(userService).searchUsers("war");
        }

        @Test
        @DisplayName("Пустой query — все книги")
        void blankQuery_loadAll() {
            when(bookRepository.findAll()).thenReturn(List.of());
            when(userService.searchUsers("  ")).thenReturn(List.of());
            when(logRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());

            adminController.adminPanel(model, "  ");

            verify(bookRepository).findAll();
        }
    }

    // ─── deleteBook ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /admin/books/{id}/delete")
    class DeleteBook {

        @Test
        @DisplayName("Книга не найдена — flash error, редирект")
        void bookNotFound_flashError() {
            when(bookRepository.findById(99L)).thenReturn(Optional.empty());

            String view = adminController.deleteBook(99L, null, adminPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(ra).addFlashAttribute(eq("error"), any());
            verify(bookRepository, never()).delete(any());
        }

        @Test
        @DisplayName("Успешное удаление — удаляет отзывы, книгу, логирует, уведомляет")
        void success_deletesAndNotifies() {
            Book book = new Book(); book.setId(10L); book.setTitle("Война");
            book.setOwner(target);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(userService.findByUsername("admin")).thenReturn(admin);

            String view = adminController.deleteBook(10L, "нарушение", adminPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(reviewRepository).deleteByBookId(10L);
            verify(bookRepository).delete(book);
            verify(logRepository).save(any(ModerationLog.class));
            verify(notificationService).sendNotification(eq("bob"), contains("удалена"), any(), any());
            verify(ra).addFlashAttribute(eq("success"), any());
        }

        @Test
        @DisplayName("Удаление без причины — уведомление без текста причины")
        void deleteWithoutReason_notifyWithoutReason() {
            Book book = new Book(); book.setId(10L); book.setTitle("Книга");
            book.setOwner(target);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(userService.findByUsername("admin")).thenReturn(admin);

            adminController.deleteBook(10L, null, adminPrincipal, ra);

            verify(notificationService).sendNotification(eq("bob"), any(),
                    argThat(body -> !body.toString().contains("Причина")), any());
        }
    }

    // ─── blockUser ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /admin/users/{id}/block")
    class BlockUser {

        @Test
        @DisplayName("Попытка заблокировать ADMIN — ошибка")
        void blockAdmin_returnsError() {
            target.setRole(User.UserRole.ADMIN);
            when(userService.findByUsername("admin")).thenReturn(admin);
            when(userService.findById(2L)).thenReturn(target);

            String view = adminController.blockUser(2L, "причина", null, adminPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(ra).addFlashAttribute(eq("error"), contains("администратора"));
            verify(userService, never()).blockUser(any(), any(), any());
        }

        @Test
        @DisplayName("Срочная блокировка — вызывает blockUser и уведомляет")
        void blockWithDays_success() {
            when(userService.findByUsername("admin")).thenReturn(admin);
            when(userService.findById(2L)).thenReturn(target);

            String view = adminController.blockUser(2L, "спам", 7, adminPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(userService).blockUser(2L, "спам", 7);
            verify(notificationService).sendNotification(eq("bob"), contains("заблокирован"), any(), any());
            verify(logRepository).save(any(ModerationLog.class));
            verify(ra).addFlashAttribute(eq("success"), any());
        }

        @Test
        @DisplayName("Бессрочная блокировка — days=null")
        void permanentBlock_success() {
            when(userService.findByUsername("admin")).thenReturn(admin);
            when(userService.findById(2L)).thenReturn(target);

            adminController.blockUser(2L, "нарушение", null, adminPrincipal, ra);

            verify(userService).blockUser(2L, "нарушение", null);
            verify(notificationService).sendNotification(eq("bob"), any(),
                    argThat(body -> body.toString().contains("Бессрочно")), any());
        }
    }

    // ─── unblockUser ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /admin/users/{id}/unblock — разблокирует и уведомляет")
    void unblockUser_success() {
        when(userService.findByUsername("admin")).thenReturn(admin);
        when(userService.findById(2L)).thenReturn(target);

        String view = adminController.unblockUser(2L, adminPrincipal, ra);

        assertThat(view).isEqualTo("redirect:/admin");
        verify(userService).unblockUser(2L);
        verify(notificationService).sendNotification(eq("bob"), contains("разблокирован"), any(), any());
        verify(logRepository).save(any(ModerationLog.class));
        verify(ra).addFlashAttribute(eq("success"), any());
    }

    // ─── changeRole ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /admin/users/{id}/role")
    class ChangeRole {

        @Test
        @DisplayName("Изменение своей роли — ошибка")
        void changeSelf_returnsError() {
            when(userService.findByUsername("admin")).thenReturn(admin);
            when(userService.findById(1L)).thenReturn(admin);

            String view = adminController.changeRole(1L, User.UserRole.USER, adminPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(ra).addFlashAttribute(eq("error"), contains("собственную"));
            verify(userService, never()).changeRole(any(), any());
        }

        @Test
        @DisplayName("Изменение роли главного admin — ошибка")
        void changeMainAdmin_returnsError() {
            User mainAdmin = new User(); mainAdmin.setId(99L); mainAdmin.setUsername("admin");
            mainAdmin.setRole(User.UserRole.ADMIN);
            User otherAdmin = new User(); otherAdmin.setId(3L); otherAdmin.setUsername("other");
            Principal otherPrincipal = () -> "other";

            when(userService.findByUsername("other")).thenReturn(otherAdmin);
            when(userService.findById(99L)).thenReturn(mainAdmin);

            String view = adminController.changeRole(99L, User.UserRole.USER, otherPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(ra).addFlashAttribute(eq("error"), contains("неизменна"));
        }

        @Test
        @DisplayName("Смена роли другого ADMIN не-суперадмином — ошибка")
        void changeAdminByNonSuper_returnsError() {
            User mod = new User(); mod.setId(3L); mod.setUsername("mod");
            mod.setRole(User.UserRole.MODERATOR);
            User otherAdmin = new User(); otherAdmin.setId(4L); otherAdmin.setUsername("otheradmin");
            otherAdmin.setRole(User.UserRole.ADMIN);
            Principal modPrincipal = () -> "mod";

            when(userService.findByUsername("mod")).thenReturn(mod);
            when(userService.findById(4L)).thenReturn(otherAdmin);

            String view = adminController.changeRole(4L, User.UserRole.USER, modPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(ra).addFlashAttribute(eq("error"), contains("главный"));
        }

        @Test
        @DisplayName("Успешная смена роли — меняет, логирует, уведомляет")
        void success_changesAndNotifies() {
            when(userService.findByUsername("admin")).thenReturn(admin);
            when(userService.findById(2L)).thenReturn(target);

            String view = adminController.changeRole(2L, User.UserRole.MODERATOR, adminPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(userService).changeRole(2L, User.UserRole.MODERATOR);
            verify(notificationService).sendNotification(eq("bob"), contains("роль"), any(), any());
            verify(logRepository).save(any(ModerationLog.class));
            verify(ra).addFlashAttribute(eq("success"), any());
        }
    }

    // ─── deleteUser ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /admin/users/{id}/delete")
    class DeleteUser {

        @Test
        @DisplayName("Удаление себя — ошибка")
        void deleteSelf_returnsError() {
            when(userService.findByUsername("admin")).thenReturn(admin);
            when(userService.findById(1L)).thenReturn(admin);

            String view = adminController.deleteUser(1L, null, adminPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(ra).addFlashAttribute(eq("error"), contains("собственный"));
            verify(userService, never()).deleteUser(any());
        }

        @Test
        @DisplayName("Удаление главного admin — ошибка")
        void deleteMainAdmin_returnsError() {
            User mainAdmin = new User(); mainAdmin.setId(99L); mainAdmin.setUsername("admin");
            User other = new User(); other.setId(3L); other.setUsername("other");
            Principal otherPrincipal = () -> "other";

            when(userService.findByUsername("other")).thenReturn(other);
            when(userService.findById(99L)).thenReturn(mainAdmin);

            String view = adminController.deleteUser(99L, null, otherPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(ra).addFlashAttribute(eq("error"), contains("нельзя"));
        }

        @Test
        @DisplayName("Удаление ADMIN не-суперадмином — ошибка")
        void deleteAdminByNonSuper_returnsError() {
            User mod = new User(); mod.setId(3L); mod.setUsername("mod");
            User otherAdmin = new User(); otherAdmin.setId(4L); otherAdmin.setUsername("otheradmin");
            otherAdmin.setRole(User.UserRole.ADMIN);
            Principal modPrincipal = () -> "mod";

            when(userService.findByUsername("mod")).thenReturn(mod);
            when(userService.findById(4L)).thenReturn(otherAdmin);

            String view = adminController.deleteUser(4L, null, modPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(ra).addFlashAttribute(eq("error"), contains("главный"));
        }

        @Test
        @DisplayName("Успешное удаление — каскадная очистка, лог, flash success")
        void success_deletesAndLogs() {
            when(userService.findByUsername("admin")).thenReturn(admin);
            when(userService.findById(2L)).thenReturn(target);

            String view = adminController.deleteUser(2L, "нарушение", adminPrincipal, ra);

            assertThat(view).isEqualTo("redirect:/admin");
            verify(userService).deleteUser(2L);
            verify(logRepository).save(any(ModerationLog.class));
            verify(ra).addFlashAttribute(eq("success"), any());
        }

        @Test
        @DisplayName("Удаление без причины — логируется без reason")
        void deleteWithoutReason_logsWithoutReason() {
            when(userService.findByUsername("admin")).thenReturn(admin);
            when(userService.findById(2L)).thenReturn(target);

            adminController.deleteUser(2L, null, adminPrincipal, ra);

            ArgumentCaptor<ModerationLog> cap = ArgumentCaptor.forClass(ModerationLog.class);
            verify(logRepository).save(cap.capture());
            assertThat(cap.getValue().getReason()).doesNotContain("null");
        }
    }
}