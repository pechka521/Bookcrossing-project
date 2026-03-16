package com.bookcrossing.controller;

import com.bookcrossing.model.*;
import com.bookcrossing.repository.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// ─────────────────────────────────────────────────────────────────────────────

class NotificationControllerTest {

    @org.junit.jupiter.api.extension.ExtendWith(MockitoExtension.class)
    @DisplayName("NotificationController")
    static class Inner {
        @Mock NotificationService notificationService;
        @InjectMocks NotificationController notificationController;

        private Principal principal;
        private Model model;

        @BeforeEach
        void setUp() {
            principal = () -> "alice";
            model = mock(Model.class);
        }

        @Test
        @DisplayName("notificationsPage — загружает уведомления и помечает прочитанными")
        void notificationsPage_loadsAndMarksRead() {
            Notification n = new Notification();
            when(notificationService.getNotifications("alice")).thenReturn(List.of(n));

            String view = notificationController.notificationsPage(model, principal);

            assertThat(view).isEqualTo("notifications");
            verify(model).addAttribute(eq("notifications"), any());
            verify(notificationService).markAllRead("alice");
        }

        @Test
        @DisplayName("markRead — вызывает сервис и возвращает ok")
        void markRead_returnsOk() {
            String result = notificationController.markRead(42L, principal);
            assertThat(result).isEqualTo("ok");
            verify(notificationService).markRead(42L);
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@ExtendWith(MockitoExtension.class)
@DisplayName("ComplaintController")
class ComplaintControllerTest {

    @Mock ComplaintRepository complaintRepository;
    @Mock UserService         userService;
    @InjectMocks ComplaintController complaintController;

    private Principal principal;
    private RedirectAttributes ra;
    private User author;

    @BeforeEach
    void setUp() {
        author = new User(); author.setId(1L); author.setUsername("alice");
        principal = () -> "alice";
        ra = mock(RedirectAttributes.class);
    }

    @Test
    @DisplayName("submitComplaint — сохраняет жалобу и редирект")
    void submit_savesAndRedirects() {
        when(userService.findByUsername("alice")).thenReturn(author);

        String view = complaintController.submitComplaint(
                10L, "Плохая книга", "SPAM", "Описание жалобы здесь", principal, ra);

        assertThat(view).isEqualTo("redirect:/");
        verify(complaintRepository).save(any(Complaint.class));
        verify(ra).addFlashAttribute(eq("success"), any());
    }

    @Test
    @DisplayName("submitComplaint — устанавливает поля жалобы правильно")
    void submit_setsCorrectFields() {
        when(userService.findByUsername("alice")).thenReturn(author);
        var cap = org.mockito.ArgumentCaptor.forClass(Complaint.class);

        complaintController.submitComplaint(
                10L, "Книга 1", "SPAM", "Нежелательный контент", principal, ra);

        verify(complaintRepository).save(cap.capture());
        Complaint saved = cap.getValue();
        assertThat(saved.getAuthor()).isEqualTo(author);
        assertThat(saved.getTargetBookId()).isEqualTo(10L);
        assertThat(saved.getTargetBookTitle()).isEqualTo("Книга 1");
        assertThat(saved.getType()).isEqualTo(Complaint.ComplaintType.SPAM);
        assertThat(saved.getCreatedAt()).isNotNull();
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@ExtendWith(MockitoExtension.class)
@DisplayName("ProfileController")
class ProfileControllerTest {

    @Mock UserService        userService;
    @Mock AchievementService achievementService;
    @InjectMocks ProfileController profileController;

    private User user;
    private Principal principal;
    private Model model;
    private RedirectAttributes ra;

    @BeforeEach
    void setUp() {
        user = new User(); user.setId(1L); user.setUsername("alice");
        user.setPassword("hashed");
        principal = () -> "alice";
        model = mock(Model.class);
        ra    = mock(RedirectAttributes.class);
    }

    @Test
    @DisplayName("myProfile — добавляет profileUser, isOwnProfile=true")
    void myProfile_populatesModel() {
        when(userService.findByUsername("alice")).thenReturn(user);
        String view = profileController.myProfile(principal, model);
        assertThat(view).isEqualTo("profile");
        verify(model).addAttribute("profileUser", user);
        verify(model).addAttribute("isOwnProfile", true);
        verify(achievementService).checkAndAward(user);
    }

    @Test
    @DisplayName("userProfile — чужой профиль, isOwnProfile=false")
    void userProfile_otherUser() {
        User other = new User(); other.setUsername("bob");
        when(userService.findByUsername("bob")).thenReturn(other);
        String view = profileController.userProfile("bob", principal, model);
        assertThat(view).isEqualTo("profile");
        verify(model).addAttribute("isOwnProfile", false);
    }

    @Test
    @DisplayName("userProfile — свой username в URL, isOwnProfile=true")
    void userProfile_ownUsername_true() {
        when(userService.findByUsername("alice")).thenReturn(user);
        profileController.userProfile("alice", principal, model);
        verify(model).addAttribute("isOwnProfile", true);
    }

    @Test
    @DisplayName("editForm — возвращает profile-edit с user в модели")
    void editForm_returnsView() {
        when(userService.findByUsername("alice")).thenReturn(user);
        String view = profileController.editForm(principal, model);
        assertThat(view).isEqualTo("profile-edit");
        verify(model).addAttribute("user", user);
    }

    @Nested
    @DisplayName("editSave")
    class EditSave {

        @Test
        @DisplayName("Некорректный email — ошибка")
        void invalidEmail_returnsError() {
            when(userService.findByUsername("alice")).thenReturn(user);

            String view = profileController.editSave(principal,
                    "Имя", "not-an-email", null, null, null, null,
                    null, null, null, null, null, null, null, ra, model);

            assertThat(view).isEqualTo("profile-edit");
            verify(model).addAttribute(eq("error"), contains("email"));
        }

        @Test
        @DisplayName("aboutMe > 1000 символов — ошибка")
        void aboutMeTooLong_returnsError() {
            when(userService.findByUsername("alice")).thenReturn(user);
            String longAbout = "а".repeat(1001);

            String view = profileController.editSave(principal,
                    null, null, null, null, null, null,
                    longAbout, null, null, null, null, null, null, ra, model);

            assertThat(view).isEqualTo("profile-edit");
            verify(model).addAttribute(eq("error"), contains("1000"));
        }

        @Test
        @DisplayName("Новый пароль < 6 символов — ошибка")
        void newPasswordTooShort_returnsError() {
            when(userService.findByUsername("alice")).thenReturn(user);

            String view = profileController.editSave(principal,
                    null, null, null, null, null, null,
                    null, null, null, "old", "abc", "abc", null, ra, model);

            assertThat(view).isEqualTo("profile-edit");
            verify(model).addAttribute(eq("error"), contains("6"));
        }

        @Test
        @DisplayName("Пароли не совпадают — ошибка")
        void passwordsMismatch_returnsError() {
            when(userService.findByUsername("alice")).thenReturn(user);

            String view = profileController.editSave(principal,
                    null, null, null, null, null, null,
                    null, null, null, "old", "NewPass1", "NewPass2", null, ra, model);

            assertThat(view).isEqualTo("profile-edit");
            verify(model).addAttribute(eq("error"), contains("совпадают"));
        }

        @Test
        @DisplayName("Успешное сохранение — обновляет поля и редирект")
        void success_savesAndRedirects() {
            when(userService.findByUsername("alice")).thenReturn(user);

            String view = profileController.editSave(principal,
                    "Новое Имя", "valid@mail.com", "Москва", "Россия",
                    "MALE", null, "Люблю книги", null, null,
                    null, null, null, null, ra, model);

            assertThat(view).isEqualTo("redirect:/profile");
            assertThat(user.getFullName()).isEqualTo("Новое Имя");
            assertThat(user.getEmail()).isEqualTo("valid@mail.com");
            verify(userService).saveUser(user);
            verify(ra).addFlashAttribute(eq("success"), any());
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@ExtendWith(MockitoExtension.class)
@DisplayName("StatsController")
class StatsControllerTest {

    @Mock UserService               userService;
    @Mock AchievementService        achievementService;
    @Mock BookRepository            bookRepository;
    @Mock ReviewRepository          reviewRepository;
    @Mock BookingRepository         bookingRepository;
    @Mock UserAchievementRepository userAchievementRepository;
    @Mock AchievementRepository     achievementRepository;
    @InjectMocks StatsController    statsController;

    private User user;
    private Principal principal;

    @BeforeEach
    void setUp() {
        user = new User(); user.setId(1L); user.setUsername("alice");
        principal = () -> "alice";
    }

    @Test
    @DisplayName("getMyStats — возвращает 200 со статистикой")
    void getMyStats_returns200() {
        AchievementService.UserStats stats = new AchievementService.UserStats();
        when(userService.findByUsername("alice")).thenReturn(user);
        when(achievementService.calculateStats(user)).thenReturn(stats);

        ResponseEntity<AchievementService.UserStats> resp = statsController.getMyStats(principal);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isEqualTo(stats);
        verify(achievementService).checkAndAward(user);
    }

    @Test
    @DisplayName("getUserStats — пользователь найден — 200")
    void getUserStats_found_200() {
        AchievementService.UserStats stats = new AchievementService.UserStats();
        when(userService.findByUsername("alice")).thenReturn(user);
        when(achievementService.calculateStats(user)).thenReturn(stats);

        ResponseEntity<?> resp = statsController.getUserStats("alice");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getUserStats — пользователь не найден — 404")
    void getUserStats_notFound_404() {
        when(userService.findByUsername("ghost")).thenThrow(new RuntimeException("not found"));

        ResponseEntity<?> resp = statsController.getUserStats("ghost");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getAchievements — возвращает список DTO")
    void getAchievements_returnsList() {
        when(userService.findByUsername("alice")).thenReturn(user);
        when(achievementService.getUserAchievements(user)).thenReturn(List.of());

        ResponseEntity<List<AchievementService.AchievementDto>> resp =
                statsController.getAchievements(principal);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getBooksAdded — пользователь существует — 200 со списком")
    void getBooksAdded_found_200() {
        Book b = new Book(); b.setId(1L); b.setTitle("T"); b.setAuthor("A");
        b.setStatus(Book.BookStatus.FREE);
        when(userService.findByUsername("alice")).thenReturn(user);
        when(bookRepository.findByOwner(user)).thenReturn(List.of(b));

        ResponseEntity<List<java.util.Map<String, Object>>> resp =
                statsController.getBooksAdded("alice");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("getBooksAdded — пользователь не найден — 404")
    void getBooksAdded_notFound_404() {
        when(userService.findByUsername("ghost")).thenThrow(new RuntimeException());

        ResponseEntity<?> resp = statsController.getBooksAdded("ghost");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getBooksGiven — возвращает только BUSY книги")
    void getBooksGiven_onlyBusyBooks() {
        Book free = new Book(); free.setId(1L); free.setTitle("F");
        free.setStatus(Book.BookStatus.FREE);
        Book busy = new Book(); busy.setId(2L); busy.setTitle("B");
        busy.setStatus(Book.BookStatus.BUSY);
        when(userService.findByUsername("alice")).thenReturn(user);
        when(bookRepository.findByOwner(user)).thenReturn(List.of(free, busy));
        when(bookingRepository.findActiveBookingForBook(eq(busy), any()))
                .thenReturn(java.util.Optional.empty());

        ResponseEntity<List<java.util.Map<String, Object>>> resp =
                statsController.getBooksGiven("alice");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }

    @Test
    @DisplayName("getReviews — возвращает список отзывов пользователя")
    void getReviews_returnsList() {
        Review r = new Review(); r.setId(1L); r.setRating(5);
        r.setComment("OK"); r.setCreatedAt(LocalDateTime.now());
        when(userService.findByUsername("alice")).thenReturn(user);
        when(reviewRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(r));

        ResponseEntity<List<java.util.Map<String, Object>>> resp =
                statsController.getReviews("alice");

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).hasSize(1);
    }
}