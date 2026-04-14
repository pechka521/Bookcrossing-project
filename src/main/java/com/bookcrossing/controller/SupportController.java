package com.bookcrossing.controller;

import com.bookcrossing.model.SupportMessage;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.SupportMessageRepository;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/support")
public class SupportController {

    private final SupportMessageRepository supportRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public SupportController(SupportMessageRepository supportRepository,
                             UserService userService,
                             NotificationService notificationService) {
        this.supportRepository = supportRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /**
     * Отправка обращения в поддержку (любой авторизованный пользователь).
     */
    @PostMapping("/submit")
    public String submit(@RequestParam String type,
                         @RequestParam String message,
                         Principal principal,
                         RedirectAttributes ra) {

        if (message == null || message.isBlank()) {
            ra.addFlashAttribute("supportError", "Текст обращения не может быть пустым.");
            return "redirect:/";
        }

        User author = userService.findByUsername(principal.getName());

        SupportMessage sm = new SupportMessage();
        sm.setAuthor(author);
        sm.setType(SupportMessage.SupportType.valueOf(type));
        sm.setMessage(message.trim());
        supportRepository.save(sm);

        ra.addFlashAttribute("supportSuccess", "Ваше обращение отправлено администратору. Спасибо!");
        return "redirect:/";
    }

    /**
     * Панель администратора — просмотр обращений.
     * Доступно только пользователям с ролью ADMIN.
     */
    @GetMapping("/admin")
    public String adminView(Model model) {
        List<SupportMessage> messages = supportRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("supportMessages", messages);
        model.addAttribute("newCount",
                supportRepository.countByStatus(SupportMessage.SupportStatus.NEW));
        model.addAttribute("types",   SupportMessage.SupportType.values());
        model.addAttribute("statuses", SupportMessage.SupportStatus.values());
        return "support-admin";
    }

    /**
     * Администратор меняет статус обращения и/или добавляет ответ.
     */
    @PostMapping("/{id}/reply")
    public String reply(@PathVariable Long id,
                        @RequestParam(required = false) String adminReply,
                        @RequestParam String status,
                        Principal principal,
                        RedirectAttributes ra) {

        SupportMessage sm = supportRepository.findById(id).orElse(null);
        if (sm == null) {
            ra.addFlashAttribute("error", "Обращение не найдено.");
            return "redirect:/support/admin";
        }

        sm.setStatus(SupportMessage.SupportStatus.valueOf(status));
        if (adminReply != null && !adminReply.isBlank()) {
            sm.setAdminReply(adminReply.trim());
            sm.setRepliedAt(LocalDateTime.now());
        }
        supportRepository.save(sm);

        // Уведомляем пользователя, если написан ответ
        if (adminReply != null && !adminReply.isBlank() && sm.getAuthor() != null) {
            notificationService.sendNotification(
                    sm.getAuthor().getUsername(),
                    "📩 Ответ на ваше обращение",
                    "Администратор ответил на ваше обращение: " + adminReply,
                    "/"
            );
        }

        ra.addFlashAttribute("success", "Обращение обновлено.");
        return "redirect:/support/admin";
    }
}