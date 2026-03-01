package com.bookcrossing.controller;

import com.bookcrossing.model.Complaint;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.ComplaintRepository;
import com.bookcrossing.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;

@Controller
@RequestMapping("/complaints")
public class ComplaintController {

    private final ComplaintRepository complaintRepository;
    private final UserService userService;

    public ComplaintController(ComplaintRepository complaintRepository,
                               UserService userService) {
        this.complaintRepository = complaintRepository;
        this.userService         = userService;
    }

    /**
     * Подача жалобы — доступна любому авторизованному пользователю.
     * (Не под /moderator/**, чтобы не требовать роль MODERATOR)
     */
    @PostMapping("/submit")
    public String submitComplaint(@RequestParam Long   targetBookId,
                                  @RequestParam String targetBookTitle,
                                  @RequestParam String type,
                                  @RequestParam String description,
                                  Principal principal,
                                  RedirectAttributes ra) {
        User author = userService.findByUsername(principal.getName());

        Complaint c = new Complaint();
        c.setAuthor(author);
        c.setTargetBookId(targetBookId);
        c.setTargetBookTitle(targetBookTitle);
        c.setType(Complaint.ComplaintType.valueOf(type));
        c.setDescription(description);
        c.setCreatedAt(LocalDateTime.now());
        complaintRepository.save(c);

        ra.addFlashAttribute("success", "Жалоба отправлена. Модераторы рассмотрят её в ближайшее время.");
        return "redirect:/";
    }
}