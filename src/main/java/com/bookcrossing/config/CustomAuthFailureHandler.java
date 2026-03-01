package com.bookcrossing.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        // DisabledException — аккаунт заблокирован
        if (exception instanceof DisabledException) {
            String msg = exception.getMessage(); // "BLOCKED|причина|срок"
            if (msg != null && msg.startsWith("BLOCKED|")) {
                String[] parts = msg.split("\\|", 3);
                String reason = parts.length > 1 ? parts[1] : "нарушение правил";
                String until  = parts.length > 2 ? parts[2] : "бессрочно";
                response.sendRedirect("/login?blocked=1"
                        + "&reason=" + URLEncoder.encode(reason, StandardCharsets.UTF_8)
                        + "&until="  + URLEncoder.encode(until,  StandardCharsets.UTF_8));
                return;
            }
        }
        // Стандартная ошибка — неверный логин/пароль
        response.sendRedirect("/login?error");
    }
}