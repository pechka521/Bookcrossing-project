package com.bookcrossing.service;

import com.bookcrossing.model.User;
import com.bookcrossing.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
public class UserService {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User findByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + username));
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден id=" + id));
    }

    public List<User> findAll() { return userRepository.findAll(); }

    public List<User> searchUsers(String q) {
        return (q == null || q.isBlank()) ? userRepository.findAll()
                : userRepository.searchUsers(q);
    }

    @Transactional
    public void register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            user.setRole(User.UserRole.ADMIN);
        }
        userRepository.save(user);
    }

    @Transactional
    public void updateProfile(User current, User data, MultipartFile avatar) {
        current.setFullName(data.getFullName());
        current.setEmail(data.getEmail());
        current.setCity(data.getCity());
        current.setCountry(data.getCountry());
        current.setGender(data.getGender());
        current.setBirthDate(data.getBirthDate());
        current.setAboutMe(data.getAboutMe());
        current.setSocialLinks(data.getSocialLinks());
        current.setFavoriteGenres(data.getFavoriteGenres());
        if (avatar != null && !avatar.isEmpty()) {
            try {
                String b64 = Base64.getEncoder().encodeToString(avatar.getBytes());
                current.setAvatarUrl("data:" + avatar.getContentType() + ";base64," + b64);
            } catch (IOException e) { throw new RuntimeException("Ошибка загрузки аватара", e); }
        }
        userRepository.save(current);
    }

    // ── Блокировка ────────────────────────────────────────────

    @Transactional
    public void blockUser(Long id, String reason, Integer days) {
        User u = findById(id);
        u.setBlocked(true);
        u.setBlockReason(reason);
        u.setBlockUntil(days != null ? LocalDateTime.now().plusDays(days) : null);
        userRepository.save(u);
    }

    @Transactional
    public void unblockUser(Long id) {
        User u = findById(id);
        u.setBlocked(false);
        u.setBlockReason(null);
        u.setBlockUntil(null);
        userRepository.save(u);
    }

    @Transactional
    public void changeRole(Long id, User.UserRole role) {
        User u = findById(id);
        u.setRole(role);
        userRepository.save(u);
    }

    // ── Удаление пользователя ─────────────────────────────────
    @Transactional
    public void deleteUser(Long id) {
        User user = findById(id);
        userRepository.deleteMessagesByUser(id);
        userRepository.deleteNotificationsByUsername(user.getUsername());
        userRepository.deleteReviewsByUser(id);
        userRepository.deleteReviewsOfUserBooks(id);
        userRepository.deleteBooksByUser(id);
        userRepository.deleteReviewsTargetingUser(id);
        userRepository.deleteById(id);
    }

    @Transactional
    public void saveUser(User user) {
        userRepository.save(user);
    }

    @Transactional
    public boolean changePassword(User user, String currentPass, String newPass,
                                  org.springframework.ui.Model model) {
        if (currentPass == null || !passwordEncoder.matches(currentPass, user.getPassword())) {
            model.addAttribute("error", "Текущий пароль введён неверно.");
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPass));
        userRepository.save(user);
        return true;
    }
}