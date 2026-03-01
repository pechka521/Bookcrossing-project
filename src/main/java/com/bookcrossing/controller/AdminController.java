package com.bookcrossing.controller;

import com.bookcrossing.model.*;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.ModerationLogRepository;
import com.bookcrossing.repository.ReviewRepository;
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
@RequestMapping("/admin")
public class AdminController {

    private final UserService userService;
    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
    private final ModerationLogRepository logRepository;
    private final NotificationService notificationService;

    public AdminController(UserService userService,
                           BookRepository bookRepository,
                           ReviewRepository reviewRepository,
                           ModerationLogRepository logRepository,
                           NotificationService notificationService) {
        this.userService        = userService;
        this.bookRepository     = bookRepository;
        this.reviewRepository   = reviewRepository;
        this.logRepository      = logRepository;
        this.notificationService = notificationService;
    }

    // ── Главная ───────────────────────────────────────────────

    @GetMapping
    public String adminPanel(Model model, @RequestParam(required = false) String query) {
        List<Book> books = (query != null && !query.isBlank())
                ? bookRepository.searchByQuery(query) : bookRepository.findAll();
        model.addAttribute("books",    books);
        model.addAttribute("query",    query);
        model.addAttribute("users",    userService.searchUsers(query));
        model.addAttribute("logs",     logRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("allRoles", User.UserRole.values());
        return "admin";
    }

    // ── Удалить книгу ─────────────────────────────────────────

    @PostMapping("/books/{id}/delete")
    public String deleteBook(@PathVariable Long id,
                             @RequestParam(required = false) String reason,
                             Principal principal, RedirectAttributes ra) {
        Book book = bookRepository.findById(id).orElse(null);
        if (book == null) { ra.addFlashAttribute("error", "Книга не найдена"); return "redirect:/admin"; }

        User mod = userService.findByUsername(principal.getName());
        String title = book.getTitle();
        User owner = book.getOwner();

        reviewRepository.deleteByBookId(id);
        bookRepository.delete(book);
        log(mod, ModerationLog.ActionType.BOOK_DELETED, owner, id, title, reason);
        notificationService.sendNotification(owner.getUsername(), "Ваша книга удалена",
                "«" + title + "» удалена." + (reason != null && !reason.isBlank() ? " Причина: " + reason : ""), "/");

        ra.addFlashAttribute("success", "Книга «" + title + "» удалена.");
        return "redirect:/admin";
    }

    // ── Заблокировать ─────────────────────────────────────────

    @PostMapping("/users/{id}/block")
    public String blockUser(@PathVariable Long id,
                            @RequestParam String reason,
                            @RequestParam(required = false) Integer days,
                            Principal principal, RedirectAttributes ra) {
        User mod    = userService.findByUsername(principal.getName());
        User target = userService.findById(id);

        if (target.getRole() == User.UserRole.ADMIN) {
            ra.addFlashAttribute("error", "Нельзя заблокировать администратора.");
            return "redirect:/admin";
        }
        userService.blockUser(id, reason, days);
        log(mod, ModerationLog.ActionType.USER_BLOCKED, target, null, null, reason);
        notificationService.sendNotification(target.getUsername(), "Ваш аккаунт заблокирован",
                "Причина: " + reason + (days != null ? ". Срок: " + days + " дней." : ". Бессрочно."), "/");

        ra.addFlashAttribute("success", "@" + target.getUsername() + " заблокирован.");
        return "redirect:/admin";
    }

    // ── Разблокировать ────────────────────────────────────────

    @PostMapping("/users/{id}/unblock")
    public String unblockUser(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        User mod    = userService.findByUsername(principal.getName());
        User target = userService.findById(id);

        userService.unblockUser(id);
        log(mod, ModerationLog.ActionType.USER_UNBLOCKED, target, null, null, "Разблокирован");
        notificationService.sendNotification(target.getUsername(), "Аккаунт разблокирован",
                "Ограничения сняты.", "/");

        ra.addFlashAttribute("success", "@" + target.getUsername() + " разблокирован.");
        return "redirect:/admin";
    }

    // ── Изменить роль ─────────────────────────────────────────

    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable Long id,
                             @RequestParam("role") User.UserRole newRole,
                             Principal principal, RedirectAttributes ra) {
        User mod    = userService.findByUsername(principal.getName());
        User target = userService.findById(id);

        // Нельзя изменить свою роль
        if (target.getUsername().equalsIgnoreCase(principal.getName())) {
            ra.addFlashAttribute("error", "Нельзя изменить собственную роль.");
            return "redirect:/admin";
        }
        // Главного "admin" не трогаем никогда
        if ("admin".equalsIgnoreCase(target.getUsername())) {
            ra.addFlashAttribute("error", "Роль главного администратора неизменна.");
            return "redirect:/admin";
        }
        // Снять роль ADMIN может только суперадмин "admin"
        if (target.getRole() == User.UserRole.ADMIN
                && !mod.getUsername().equalsIgnoreCase("admin")) {
            ra.addFlashAttribute("error",
                    "Только главный администратор (admin) может изменять роль других администраторов.");
            return "redirect:/admin";
        }

        userService.changeRole(id, newRole);
        log(mod, ModerationLog.ActionType.ROLE_CHANGED, target, null, null, "Роль → " + newRole);
        notificationService.sendNotification(target.getUsername(), "Ваша роль изменена",
                "Вам назначена роль: " + newRole.name(), "/");

        ra.addFlashAttribute("success", "Роль @" + target.getUsername() + " → " + newRole + ".");
        return "redirect:/admin";
    }

    // ── Удалить аккаунт ───────────────────────────────────────

    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable Long id,
                             @RequestParam(required = false) String reason,
                             Principal principal, RedirectAttributes ra) {
        User mod    = userService.findByUsername(principal.getName());
        User target = userService.findById(id);

        if (target.getUsername().equalsIgnoreCase(principal.getName())) {
            ra.addFlashAttribute("error", "Нельзя удалить собственный аккаунт."); return "redirect:/admin";
        }
        if ("admin".equalsIgnoreCase(target.getUsername())) {
            ra.addFlashAttribute("error", "Главного администратора удалить нельзя."); return "redirect:/admin";
        }
        if (target.getRole() == User.UserRole.ADMIN
                && !mod.getUsername().equalsIgnoreCase("admin")) {
            ra.addFlashAttribute("error",
                    "Только главный администратор может удалять других администраторов.");
            return "redirect:/admin";
        }

        String username = target.getUsername();

        // Лог пишем без target_user (FK на удаляемую строку нельзя хранить)
        ModerationLog entry = new ModerationLog();
        entry.setModerator(mod);
        entry.setAction(ModerationLog.ActionType.USER_DELETED);
        entry.setTargetUser(null);
        entry.setReason("Удалён @" + username + (reason != null && !reason.isBlank() ? ". " + reason : ""));
        entry.setCreatedAt(LocalDateTime.now());
        logRepository.save(entry);

        // Полная каскадная очистка + удаление
        userService.deleteUser(id);

        ra.addFlashAttribute("success", "Аккаунт @" + username + " удалён.");
        return "redirect:/admin";
    }

    // ── Утилита ───────────────────────────────────────────────

    private void log(User mod, ModerationLog.ActionType action, User target,
                     Long bookId, String bookTitle, String reason) {
        ModerationLog e = new ModerationLog();
        e.setModerator(mod);
        e.setAction(action);
        e.setTargetUser(target);
        e.setBookId(bookId);
        e.setBookTitle(bookTitle);
        e.setReason(reason);
        e.setCreatedAt(LocalDateTime.now());
        logRepository.save(e);
    }
}