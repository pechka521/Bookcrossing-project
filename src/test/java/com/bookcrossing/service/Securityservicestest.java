package com.bookcrossing.service;

import com.bookcrossing.model.Achievement;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.AchievementRepository;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.ComplaintRepository;
import com.bookcrossing.repository.ReviewRepository;
import com.bookcrossing.repository.UserAchievementRepository;
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
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// ─────────────────────────────────────────────────────────────────────────────

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl")
class UserDetailsServiceImplTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserDetailsServiceImpl service;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("$2a$10$hashed");
        user.setRole(User.UserRole.USER);
        user.setBlocked(false);
    }

    @Test
    @DisplayName("Пользователь найден и не заблокирован — возвращает UserDetails")
    void found_notBlocked_returnsDetails() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isEqualTo("$2a$10$hashed");
        assertThat(details.getAuthorities()).hasSize(1);
        assertThat(details.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("Роль ADMIN — authority = ROLE_ADMIN")
    void adminRole_correctAuthority() {
        user.setRole(User.UserRole.ADMIN);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_ADMIN");
    }

    @Test
    @DisplayName("Роль MODERATOR — authority = ROLE_MODERATOR")
    void moderatorRole_correctAuthority() {
        user.setRole(User.UserRole.MODERATOR);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_MODERATOR");
    }

    @Test
    @DisplayName("Пользователь не найден — UsernameNotFoundException")
    void notFound_throwsUsernameNotFound() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    @DisplayName("Бессрочная блокировка — DisabledException с причиной")
    void permanentBlock_throwsDisabled() {
        user.setBlocked(true);
        user.setBlockUntil(null);
        user.setBlockReason("спам");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUserByUsername("alice"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("BLOCKED")
                .hasMessageContaining("спам")
                .hasMessageContaining("бессрочно");
    }

    @Test
    @DisplayName("Срочная блокировка — DisabledException с датой окончания")
    void temporaryBlock_throwsDisabledWithDate() {
        user.setBlocked(true);
        user.setBlockUntil(LocalDateTime.of(2030, 12, 31, 23, 59));
        user.setBlockReason("нарушение");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUserByUsername("alice"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("31.12.2030");
    }

    @Test
    @DisplayName("Блокировка с reason=null — использует 'нарушение правил' по умолчанию")
    void blockWithNullReason_usesDefault() {
        user.setBlocked(true);
        user.setBlockReason(null);
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.loadUserByUsername("alice"))
                .isInstanceOf(DisabledException.class)
                .hasMessageContaining("нарушение правил");
    }

    @Test
    @DisplayName("Истёкшая блокировка — пользователь проходит аутентификацию")
    void expiredBlock_allowsLogin() {
        user.setBlocked(true);
        user.setBlockUntil(LocalDateTime.now().minusDays(1)); // истёк вчера
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));

        // не бросает исключение
        assertThatNoException().isThrownBy(() -> service.loadUserByUsername("alice"));
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@ExtendWith(MockitoExtension.class)
@DisplayName("AchievementService — initAchievements")
class AchievementServiceInitTest {

    @Mock AchievementRepository     achievementRepository;
    @Mock UserAchievementRepository userAchievementRepository;
    @Mock BookRepository            bookRepository;
    @Mock ReviewRepository          reviewRepository;
    @Mock ComplaintRepository       complaintRepository;
    @Mock NotificationService       notificationService;
    @InjectMocks AchievementService achievementService;

    @Test
    @DisplayName("initAchievements — создаёт 12 достижений если БД пуста")
    void init_creates12Achievements_whenEmpty() {
        when(achievementRepository.findByCode(anyString())).thenReturn(Optional.empty());

        achievementService.initAchievements();

        ArgumentCaptor<Achievement> cap = ArgumentCaptor.forClass(Achievement.class);
        verify(achievementRepository, times(12)).save(cap.capture());
        List<Achievement> saved = cap.getAllValues();
        assertThat(saved).extracting(Achievement::getCode)
                .containsExactlyInAnyOrder(
                        "FIRST_BOOK", "BOOKWORM_5", "LIBRARIAN_10", "LEGEND_25",
                        "FIRST_SHARE", "GENEROUS_5", "PATRON_10",
                        "FIRST_REVIEW", "REVIEWER_5",
                        "VETERAN_30", "SENIOR_90", "ACTIVIST_365"
                );
    }

    @Test
    @DisplayName("initAchievements — не создаёт уже существующие достижения")
    void init_skipsExistingAchievements() {
        // Все уже существуют
        Achievement existing = new Achievement();
        when(achievementRepository.findByCode(anyString())).thenReturn(Optional.of(existing));

        achievementService.initAchievements();

        verify(achievementRepository, never()).save(any());
    }

    @Test
    @DisplayName("initAchievements — создаёт только отсутствующие достижения")
    void init_createsOnlyMissing() {
        // Только FIRST_BOOK отсутствует
        when(achievementRepository.findByCode("FIRST_BOOK")).thenReturn(Optional.empty());
        when(achievementRepository.findByCode(argThat(code -> !code.equals("FIRST_BOOK"))))
                .thenReturn(Optional.of(new Achievement()));

        achievementService.initAchievements();

        verify(achievementRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Сохранённые достижения имеют корректные типы")
    void init_correctAchievementTypes() {
        when(achievementRepository.findByCode(anyString())).thenReturn(Optional.empty());

        achievementService.initAchievements();

        ArgumentCaptor<Achievement> cap = ArgumentCaptor.forClass(Achievement.class);
        verify(achievementRepository, times(12)).save(cap.capture());

        List<Achievement> saved = cap.getAllValues();
        assertThat(saved).anySatisfy(a -> {
            assertThat(a.getType()).isEqualTo(Achievement.AchievementType.BOOKS_ADDED);
            assertThat(a.getConditionValue()).isGreaterThan(0);
        });
        assertThat(saved).anySatisfy(a ->
                assertThat(a.getType()).isEqualTo(Achievement.AchievementType.DAYS_IN_SYSTEM));
        assertThat(saved).anySatisfy(a ->
                assertThat(a.getType()).isEqualTo(Achievement.AchievementType.REVIEWS_WRITTEN));
    }
}