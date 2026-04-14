package com.bookcrossing.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final CustomAuthFailureHandler authFailureHandler;

    public SecurityConfig(UserDetailsService userDetailsService,
                          CustomAuthFailureHandler authFailureHandler) {
        this.userDetailsService = userDetailsService;
        this.authFailureHandler = authFailureHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationEntryPoint welcomeEntryPoint() {
        return new AuthenticationEntryPoint() {
            @Override
            public void commence(HttpServletRequest request,
                                 HttpServletResponse response,
                                 AuthenticationException authException) throws IOException {
                String uri = request.getRequestURI();
                if (uri.startsWith("/css/") || uri.startsWith("/js/")
                        || uri.startsWith("/images/") || uri.startsWith("/sounds/")
                        || uri.startsWith("/api/") || uri.startsWith("/ws/")) {
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                response.sendRedirect(request.getContextPath() + "/welcome");
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
                .cors(cors -> {})
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/welcome", "/register", "/login",
                                "/css/**", "/js/**", "/images/**", "/sounds/**"
                        ).permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Просмотр обращений — только ADMIN
                        .requestMatchers("/support/admin", "/support/*/reply").hasRole("ADMIN")
                        .requestMatchers("/moderator/**").hasAnyRole("ADMIN", "MODERATOR")
                        .requestMatchers("/complaints/**", "/support/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .failureHandler(authFailureHandler)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/welcome")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(welcomeEntryPoint())
                        .accessDeniedPage("/access-denied")
                );

        return http.build();
    }
}