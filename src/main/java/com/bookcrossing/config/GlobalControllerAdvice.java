package com.bookcrossing.config;

import com.bookcrossing.model.User;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final UserService userService;
    private final NotificationService notificationService;

    public GlobalControllerAdvice(UserService userService,
                                  NotificationService notificationService) {
        this.userService = userService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("currentUri")
    public String getCurrentUri(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute("currentUser")
    public User getCurrentUser(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) return null;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(auth)) return null;
        try {
            return userService.findByUsername(auth.getName());
        } catch (Exception e) {
            return null;
        }
    }

    @ModelAttribute("unreadNotifCount")
    public long getUnreadNotifCount(HttpServletRequest request) {
        // Та же причина — пропускаем /api/** чтобы не ломать REST-запросы
        if (request.getRequestURI().startsWith("/api/")) return 0L;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(auth)) return 0L;
        try {
            return notificationService.getUnreadCount(auth.getName());
        } catch (Exception e) {
            return 0L;
        }
    }

    private boolean isAuthenticated(Authentication auth) {
        return auth != null
                && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal());
    }
}