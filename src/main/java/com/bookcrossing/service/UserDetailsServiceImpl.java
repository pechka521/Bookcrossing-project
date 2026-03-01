package com.bookcrossing.service;

import com.bookcrossing.model.User;
import com.bookcrossing.repository.UserRepository;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));

        // ── Проверка блокировки — выбрасываем DisabledException ──────────
        // Spring Security показывает его на странице логина как отдельную ошибку
        if (user.isCurrentlyBlocked()) {
            String reason = user.getBlockReason() != null
                    ? user.getBlockReason() : "нарушение правил";
            String until;
            if (user.getBlockUntil() == null) {
                until = "бессрочно";
            } else {
                until = "до " + user.getBlockUntil()
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));
            }
            // Сообщение передаётся через URL ?blocked=... на страницу логина
            throw new DisabledException("BLOCKED|" + reason + "|" + until);
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPassword())
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
                ))
                .build();
    }
}