package com.bookcrossing.controller;

import com.bookcrossing.model.*;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.ComplaintRepository;
import com.bookcrossing.repository.ModerationLogRepository;
import com.bookcrossing.repository.ReviewRepository;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/moderator")
public class ModeratorController {

    private final ComplaintRepository complaintRepository;
    private final ModerationLogRepository logRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;

    public ModeratorController(ComplaintRepository complaintRepository,
                               ModerationLogRepository logRepository,
                               UserService userService,
                               NotificationService notificationService,
                               BookRepository bookRepository,
                               ReviewRepository reviewRepository) {
        this.complaintRepository = complaintRepository;
        this.logRepository       = logRepository;
        this.userService         = userService;
        this.notificationService = notificationService;
        this.bookRepository      = bookRepository;
        this.reviewRepository    = reviewRepository;
    }

    // ── Панель модератора ─────────────────────────────────────

    @GetMapping
    public String moderatorPanel(Model model,
                                 @RequestParam(required = false) String status,
                                 Principal principal) {
        var complaints = (status != null && !status.isBlank())
                ? complaintRepository.findByStatusOrderByCreatedAtDesc(
                Complaint.ComplaintStatus.valueOf(status))
                : complaintRepository.findAllByOrderByCreatedAtDesc();

        model.addAttribute("complaints",     complaints);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses",       Complaint.ComplaintStatus.values());

        model.addAttribute("pendingCount",
                complaintRepository.countByStatus(Complaint.ComplaintStatus.PENDING));
        model.addAttribute("acceptedCount",
                complaintRepository.countByStatus(Complaint.ComplaintStatus.ACCEPTED));
        model.addAttribute("rejectedCount",
                complaintRepository.countByStatus(Complaint.ComplaintStatus.REJECTED));
        model.addAttribute("myResolved",
                complaintRepository.countResolvedByModerator(principal.getName()));

        model.addAttribute("myLogs",
                logRepository.findByModeratorUsernameOrderByCreatedAtDesc(principal.getName()));

        return "moderator";
    }

    // ── Принять жалобу (просто статус) ───────────────────────

    @Transactional
    @PostMapping("/complaints/{id}/accept")
    public String acceptComplaint(@PathVariable Long id,
                                  @RequestParam(required = false) String comment,
                                  Principal principal,
                                  RedirectAttributes ra) {
        Complaint c = findComplaint(id);
        User moderator = userService.findByUsername(principal.getName());

        c.setStatus(Complaint.ComplaintStatus.ACCEPTED);
        c.setModeratorComment(comment);
        c.setResolvedBy(moderator);
        c.setResolvedAt(LocalDateTime.now());
        complaintRepository.save(c);

        notificationService.sendNotification(c.getAuthor().getUsername(),
                "Ваша жалоба принята",
                "Жалоба на «" + c.getTargetBookTitle() + "» рассмотрена и принята.", "/notifications");

        saveLog(moderator, ModerationLog.ActionType.COMPLAINT_ACCEPTED, c.getAuthor(), comment);

        ra.addFlashAttribute("success", "Жалоба принята.");
        return "redirect:/moderator";
    }

    // ── Отклонить жалобу ──────────────────────────────────────

    @Transactional
    @PostMapping("/complaints/{id}/reject")
    public String rejectComplaint(@PathVariable Long id,
                                  @RequestParam(required = false) String comment,
                                  Principal principal,
                                  RedirectAttributes ra) {
        Complaint c = findComplaint(id);
        User moderator = userService.findByUsername(principal.getName());

        c.setStatus(Complaint.ComplaintStatus.REJECTED);
        c.setModeratorComment(comment);
        c.setResolvedBy(moderator);
        c.setResolvedAt(LocalDateTime.now());
        complaintRepository.save(c);

        notificationService.sendNotification(c.getAuthor().getUsername(),
                "Ваша жалоба отклонена",
                "Жалоба на «" + c.getTargetBookTitle() + "» отклонена." +
                        (comment != null && !comment.isBlank() ? " Комментарий: " + comment : ""),
                "/notifications");

        saveLog(moderator, ModerationLog.ActionType.COMPLAINT_REJECTED, c.getAuthor(), comment);

        ra.addFlashAttribute("success", "Жалоба отклонена.");
        return "redirect:/moderator";
    }

    // ── Уведомить автора книги об исправлении ─────────────────

    @Transactional
    @PostMapping("/complaints/{id}/notify-author")
    public String notifyAuthor(@PathVariable Long id,
                               @RequestParam(required = false) String comment,
                               Principal principal,
                               RedirectAttributes ra) {
        Complaint c = findComplaint(id);
        User moderator = userService.findByUsername(principal.getName());

        // Находим владельца книги
        String bookTitle = c.getTargetBookTitle();
        Book book = c.getTargetBookId() != null
                ? bookRepository.findById(c.getTargetBookId()).orElse(null)
                : null;

        if (book != null) {
            notificationService.sendNotification(book.getOwner().getUsername(),
                    "Жалоба на вашу книгу",
                    "На книгу «" + bookTitle + "» поступила жалоба. " +
                            "Пожалуйста, исправьте нарушение." +
                            (comment != null && !comment.isBlank() ? " Комментарий модератора: " + comment : ""),
                    "/my-books");
        }

        // Меняем статус жалобы на принята (но книга не удалена)
        c.setStatus(Complaint.ComplaintStatus.ACCEPTED);
        c.setModeratorComment("Автор уведомлён. " + (comment != null ? comment : ""));
        c.setResolvedBy(moderator);
        c.setResolvedAt(LocalDateTime.now());
        complaintRepository.save(c);

        saveLog(moderator, ModerationLog.ActionType.COMPLAINT_ACCEPTED, c.getAuthor(),
                "Автор уведомлён об исправлении");

        ra.addFlashAttribute("success", "Автор книги «" + bookTitle + "» уведомлён.");
        return "redirect:/moderator";
    }

    // ── Удалить книгу по жалобе ───────────────────────────────

    @Transactional
    @PostMapping("/complaints/{id}/delete-book")
    public String deleteBookByComplaint(@PathVariable Long id,
                                        @RequestParam(required = false) String comment,
                                        Principal principal,
                                        RedirectAttributes ra) {
        Complaint c = findComplaint(id);
        User moderator = userService.findByUsername(principal.getName());
        String bookTitle = c.getTargetBookTitle();

        Book book = c.getTargetBookId() != null
                ? bookRepository.findById(c.getTargetBookId()).orElse(null)
                : null;

        if (book != null) {
            User owner = book.getOwner();
            reviewRepository.deleteByBookId(book.getId());
            bookRepository.delete(book);

            // Уведомляем автора жалобы и владельца книги
            notificationService.sendNotification(owner.getUsername(),
                    "Ваша книга удалена модератором",
                    "«" + bookTitle + "» удалена по жалобе." +
                            (comment != null && !comment.isBlank() ? " Причина: " + comment : ""),
                    "/my-books");

            ModerationLog bookLog = new ModerationLog();
            bookLog.setModerator(moderator);
            bookLog.setAction(ModerationLog.ActionType.BOOK_DELETED);
            bookLog.setTargetUser(owner);
            bookLog.setBookId(c.getTargetBookId());
            bookLog.setBookTitle(bookTitle);
            bookLog.setReason("Удаление по жалобе. " + (comment != null ? comment : ""));
            bookLog.setCreatedAt(LocalDateTime.now());
            logRepository.save(bookLog);
        }

        // Закрываем жалобу
        c.setStatus(Complaint.ComplaintStatus.ACCEPTED);
        c.setModeratorComment("Книга удалена. " + (comment != null ? comment : ""));
        c.setResolvedBy(moderator);
        c.setResolvedAt(LocalDateTime.now());
        complaintRepository.save(c);

        notificationService.sendNotification(c.getAuthor().getUsername(),
                "Ваша жалоба удовлетворена",
                "Книга «" + bookTitle + "» удалена из каталога.", "/notifications");

        saveLog(moderator, ModerationLog.ActionType.COMPLAINT_ACCEPTED, c.getAuthor(),
                "Книга удалена: " + bookTitle);

        ra.addFlashAttribute("success", "Книга «" + bookTitle + "» удалена, жалоба закрыта.");
        return "redirect:/moderator";
    }

    private Complaint findComplaint(Long id) {
        return complaintRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Жалоба не найдена: " + id));
    }

    private void saveLog(User moderator, ModerationLog.ActionType action,
                         User target, String reason) {
        ModerationLog log = new ModerationLog();
        log.setModerator(moderator);
        log.setAction(action);
        log.setTargetUser(target);
        log.setReason(reason);
        log.setCreatedAt(LocalDateTime.now());
        logRepository.save(log);
    }
}