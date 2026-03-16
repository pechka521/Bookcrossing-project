package com.bookcrossing.controller;

import com.bookcrossing.model.User;
import com.bookcrossing.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {
    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(@ModelAttribute User user,
                               @RequestParam String confirmPassword, // Поле подтверждения
                               Model model) {

        // 1. Проверка: Пароли совпадают?
        if (!user.getPassword().equals(confirmPassword)) {
            model.addAttribute("error", "Пароли не совпадают!");
            return "register";
        }

        // 2. Проверка: Длина пароля
        if (user.getPassword().length() < 8) {
            model.addAttribute("error", "Пароль должен быть не менее 8 символов!");
            return "register";
        }

        // 3. Проверка: Русские буквы (разрешаем только латиницу и символы)
        if (!user.getPassword().matches("^[a-zA-Z0-9!@#$%^&*()_+\\-=]+$")) {
            model.addAttribute("error", "Пароль может содержать только латинские буквы и цифры!");
            return "register";
        }

        // 4. Проверка: Занят ли логин?
        if (userService.existsByUsername(user.getUsername())) {
            model.addAttribute("error", "Пользователь с таким именем уже существует!");
            return "register";
        }

        userService.register(user);
        return "redirect:/login?success";
    }
}