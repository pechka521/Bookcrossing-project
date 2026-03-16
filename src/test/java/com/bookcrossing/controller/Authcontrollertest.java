package com.bookcrossing.controller;

import com.bookcrossing.model.User;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock UserService userService;
    @InjectMocks AuthController authController;

    private User user;
    private Model model;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setUsername("newuser");
        user.setPassword("ValidPass1");
        model = mock(Model.class);
    }

    @Test
    @DisplayName("GET /login — возвращает login")
    void login_returnsLoginView() {
        assertThat(authController.login()).isEqualTo("login");
    }

    @Test
    @DisplayName("GET /register — добавляет user в модель, возвращает register")
    void registerForm_returnsRegisterView() {
        String view = authController.registerForm(model);
        assertThat(view).isEqualTo("register");
        verify(model).addAttribute(eq("user"), any(User.class));
    }

    @Nested
    @DisplayName("POST /register")
    class RegisterUser {

        @Test
        @DisplayName("Пароли не совпадают — ошибка, возвращает register")
        void passwordsMismatch_returnsError() {
            String view = authController.registerUser(user, "Different1", model);
            assertThat(view).isEqualTo("register");
            verify(model).addAttribute(eq("error"), contains("совпадают"));
            verify(userService, never()).register(any());
        }

        @Test
        @DisplayName("Пароль короче 8 символов — ошибка")
        void passwordTooShort_returnsError() {
            user.setPassword("Abc123");
            String view = authController.registerUser(user, "Abc123", model);
            assertThat(view).isEqualTo("register");
            verify(model).addAttribute(eq("error"), contains("8"));
        }

        @Test
        @DisplayName("Пароль с кириллицей — ошибка")
        void passwordWithCyrillic_returnsError() {
            user.setPassword("Пароль123");
            String view = authController.registerUser(user, "Пароль123", model);
            assertThat(view).isEqualTo("register");
            verify(model).addAttribute(eq("error"), contains("латинские"));
        }

        @Test
        @DisplayName("Логин уже занят — ошибка")
        void usernameTaken_returnsError() {
            User existing = new User();
            existing.setId(99L);
            when(userService.existsByUsername("newuser")).thenReturn(true);

            String view = authController.registerUser(user, "ValidPass1", model);

            assertThat(view).isEqualTo("register");
            verify(model).addAttribute(eq("error"), contains("уже существует"));
            verify(userService, never()).register(any());
        }

        @Test
        @DisplayName("Успешная регистрация — редирект на /login?success")
        void success_redirectsToLogin() {
            when(userService.existsByUsername("newuser")).thenReturn(false);
            String view = authController.registerUser(user, "ValidPass1", model);

            assertThat(view).isEqualTo("redirect:/login?success");
            verify(userService).register(user);
        }
    }
}