package com.bookcrossing.service;

import com.bookcrossing.model.User;
import com.bookcrossing.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @InjectMocks UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPassword("hashed");
        user.setEmail("test@example.com");
        user.setRole(User.UserRole.USER);
        user.setBlocked(false);
    }

    // ─── findByUsername ───────────────────────────────────────────────────────

    @Test
    @DisplayName("findByUsername — найден — возвращает пользователя")
    void findByUsername_found() {
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        assertThat(userService.findByUsername("testuser")).isEqualTo(user);
    }

    @Test
    @DisplayName("findByUsername — не найден — бросает RuntimeException")
    void findByUsername_notFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.findByUsername("ghost"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("ghost");
    }

    // ─── findById ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById — найден — возвращает пользователя")
    void findById_found() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertThat(userService.findById(1L)).isEqualTo(user);
    }

    @Test
    @DisplayName("findById — не найден — бросает RuntimeException")
    void findById_notFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("99");
    }

    // ─── searchUsers ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("searchUsers — null query — возвращает всех")
    void searchUsers_nullQuery_returnsAll() {
        when(userRepository.findAll()).thenReturn(List.of(user));
        assertThat(userService.searchUsers(null)).hasSize(1);
        verify(userRepository).findAll();
    }

    @Test
    @DisplayName("searchUsers — blank query — возвращает всех")
    void searchUsers_blankQuery_returnsAll() {
        when(userRepository.findAll()).thenReturn(List.of(user));
        assertThat(userService.searchUsers("  ")).hasSize(1);
    }

    @Test
    @DisplayName("searchUsers — с запросом — делегирует в repository")
    void searchUsers_withQuery() {
        when(userRepository.searchUsers("test")).thenReturn(List.of(user));
        assertThat(userService.searchUsers("test")).containsExactly(user);
        verify(userRepository).searchUsers("test");
    }

    // ─── register ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register — хэширует пароль и сохраняет")
    void register_encodesPasswordAndSaves() {
        user.setPassword("plaintext");
        when(passwordEncoder.encode("plaintext")).thenReturn("hashed_pw");

        userService.register(user);

        assertThat(user.getPassword()).isEqualTo("hashed_pw");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("register — username 'admin' — роль ADMIN")
    void register_adminUsername_getsAdminRole() {
        user.setUsername("admin");
        user.setPassword("pw");
        when(passwordEncoder.encode("pw")).thenReturn("h");

        userService.register(user);

        assertThat(user.getRole()).isEqualTo(User.UserRole.ADMIN);
    }

    @Test
    @DisplayName("register — username 'ADMIN' (uppercase) — тоже ADMIN")
    void register_adminUsernameCaseInsensitive() {
        user.setUsername("ADMIN");
        user.setPassword("pw");
        when(passwordEncoder.encode("pw")).thenReturn("h");

        userService.register(user);

        assertThat(user.getRole()).isEqualTo(User.UserRole.ADMIN);
    }

    @Test
    @DisplayName("register — обычный username — роль не меняется")
    void register_normalUser_roleUnchanged() {
        user.setUsername("ivan");
        user.setRole(User.UserRole.USER);
        user.setPassword("pw");
        when(passwordEncoder.encode("pw")).thenReturn("h");

        userService.register(user);

        assertThat(user.getRole()).isEqualTo(User.UserRole.USER);
    }

    // ─── blockUser / unblockUser ──────────────────────────────────────────────

    @Test
    @DisplayName("blockUser — срочная блокировка — blockUntil задан")
    void blockUser_withDays_setsBlockUntil() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.blockUser(1L, "spam", 7);

        assertThat(user.getBlocked()).isTrue();
        assertThat(user.getBlockReason()).isEqualTo("spam");
        assertThat(user.getBlockUntil()).isNotNull();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("blockUser — бессрочно — blockUntil null")
    void blockUser_noDays_permanentBlock() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.blockUser(1L, "abuse", null);

        assertThat(user.getBlocked()).isTrue();
        assertThat(user.getBlockUntil()).isNull();
    }

    @Test
    @DisplayName("unblockUser — снимает блокировку")
    void unblockUser_clearsBlock() {
        user.setBlocked(true);
        user.setBlockReason("spam");
        user.setBlockUntil(LocalDateTime.now().plusDays(7));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.unblockUser(1L);

        assertThat(user.getBlocked()).isFalse();
        assertThat(user.getBlockReason()).isNull();
        assertThat(user.getBlockUntil()).isNull();
        verify(userRepository).save(user);
    }

    // ─── changeRole ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("changeRole — меняет роль и сохраняет")
    void changeRole_updatesAndSaves() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.changeRole(1L, User.UserRole.MODERATOR);

        assertThat(user.getRole()).isEqualTo(User.UserRole.MODERATOR);
        verify(userRepository).save(user);
    }

    // ─── changePassword ───────────────────────────────────────────────────────

    @Test
    @DisplayName("changePassword — правильный текущий пароль — возвращает true")
    void changePassword_correct_returnsTrue() {
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("new_hashed");
        Model model = mock(Model.class);

        boolean result = userService.changePassword(user, "old", "new", model);

        assertThat(result).isTrue();
        assertThat(user.getPassword()).isEqualTo("new_hashed");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("changePassword — неверный текущий пароль — возвращает false")
    void changePassword_wrong_returnsFalse() {
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);
        Model model = mock(Model.class);

        boolean result = userService.changePassword(user, "wrong", "new", model);

        assertThat(result).isFalse();
        verify(model).addAttribute(eq("error"), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("changePassword — null currentPass — возвращает false")
    void changePassword_nullCurrentPass_returnsFalse() {
        Model model = mock(Model.class);

        boolean result = userService.changePassword(user, null, "new", model);

        assertThat(result).isFalse();
        verify(model).addAttribute(eq("error"), anyString());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ─── updateProfile ────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile — без аватара — обновляет поля")
    void updateProfile_noAvatar_updatesFields() {
        User newData = new User();
        newData.setFullName("Новое Имя");
        newData.setEmail("new@test.com");
        newData.setCity("Москва");

        userService.updateProfile(user, newData, null);

        assertThat(user.getFullName()).isEqualTo("Новое Имя");
        assertThat(user.getEmail()).isEqualTo("new@test.com");
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("updateProfile — с аватаром — устанавливает base64 avatarUrl")
    void updateProfile_withAvatar_setsBase64() throws IOException {
        MultipartFile avatar = mock(MultipartFile.class);
        when(avatar.isEmpty()).thenReturn(false);
        when(avatar.getBytes()).thenReturn("avatar".getBytes());
        when(avatar.getContentType()).thenReturn("image/png");

        userService.updateProfile(user, new User(), avatar);

        assertThat(user.getAvatarUrl()).startsWith("data:image/png;base64,");
    }

    @Test
    @DisplayName("updateProfile — ошибка чтения аватара — бросает RuntimeException")
    void updateProfile_avatarReadError_throws() throws IOException {
        MultipartFile avatar = mock(MultipartFile.class);
        when(avatar.isEmpty()).thenReturn(false);
        when(avatar.getBytes()).thenThrow(new IOException("disk error"));

        assertThatThrownBy(() -> userService.updateProfile(user, new User(), avatar))
                .isInstanceOf(RuntimeException.class);
    }

    // ─── deleteUser ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteUser — вызывает каскадное удаление")
    void deleteUser_cascadeDelete() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        userService.deleteUser(1L);

        verify(userRepository).deleteMessagesByUser(1L);
        verify(userRepository).deleteNotificationsByUsername("testuser");
        verify(userRepository).deleteReviewsByUser(1L);
        verify(userRepository).deleteReviewsOfUserBooks(1L);
        verify(userRepository).deleteBooksByUser(1L);
        verify(userRepository).deleteReviewsTargetingUser(1L);
        verify(userRepository).deleteById(1L);
    }
}