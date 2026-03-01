package com.bookcrossing.controller;

import com.bookcrossing.model.*;
import com.bookcrossing.model.Booking.BookingStatus;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.BookingRepository;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/bookings")
public class BookingController {

    private final BookingRepository   bookingRepository;
    private final BookRepository      bookRepository;
    private final UserService         userService;
    private final NotificationService notificationService;

    public BookingController(BookingRepository bookingRepository,
                             BookRepository bookRepository,
                             UserService userService,
                             NotificationService notificationService) {
        this.bookingRepository   = bookingRepository;
        this.bookRepository      = bookRepository;
        this.userService         = userService;
        this.notificationService = notificationService;
    }

    // ── Запрос на бронирование ────────────────────────────────────────

    @Transactional
    @PostMapping("/request")
    public String requestBooking(@RequestParam Long bookId,
                                 @RequestParam(required = false) String message,
                                 @RequestParam(required = false) Integer days,
                                 Principal principal,
                                 RedirectAttributes ra) {
        User requester = userService.findByUsername(principal.getName());
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new IllegalArgumentException("Книга не найдена"));

        if (book.getOwner().getId().equals(requester.getId())) {
            ra.addFlashAttribute("error", "Нельзя забронировать собственную книгу.");
            return "redirect:/";
        }
        if (book.getStatus() != Book.BookStatus.FREE) {
            ra.addFlashAttribute("error", "Книга уже занята или забронирована.");
            return "redirect:/";
        }
        if (bookingRepository.existsByBookAndRequesterAndStatus(
                book, requester, BookingStatus.PENDING)) {
            ra.addFlashAttribute("error", "Вы уже отправили заявку на эту книгу.");
            return "redirect:/";
        }

        Booking booking = new Booking();
        booking.setBook(book);
        booking.setRequester(requester);
        booking.setOwner(book.getOwner());
        booking.setStatus(BookingStatus.PENDING);
        if (message != null && !message.isBlank()) booking.setMessage(message.trim());
        if (days != null && days > 0) booking.setBookedUntil(LocalDateTime.now().plusDays(days));
        bookingRepository.save(booking);

        notificationService.sendNotification(
                book.getOwner().getUsername(),
                "📚 Запрос на бронирование",
                "@" + requester.getUsername() + " хочет забронировать «" + book.getTitle() + "»." +
                        (message != null && !message.isBlank() ? " Сообщение: " + message : ""),
                "/my-books"
        );

        ra.addFlashAttribute("success",
                "Запрос отправлен! Ждите ответа от владельца.");
        return "redirect:/";
    }

    // ── Одобрить бронь ───────────────────────────────────────────────

    @Transactional
    @PostMapping("/{id}/accept")
    public String acceptBooking(@PathVariable Long id,
                                @RequestParam(required = false) String response,
                                Principal principal, RedirectAttributes ra) {
        Booking booking = findPendingForOwner(id, principal.getName());
        if (booking == null) {
            ra.addFlashAttribute("error", "Заявка не найдена или уже обработана.");
            return "redirect:/my-books";
        }

        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setOwnerResponse(response != null ? response.trim() : null);
        booking.setRespondedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        // ← Теперь ставим BOOKED (Забронирована), не BUSY
        Book book = booking.getBook();
        book.setStatus(Book.BookStatus.BOOKED);
        bookRepository.save(book);

        notificationService.sendNotification(
                booking.getRequester().getUsername(),
                "✅ Бронирование одобрено!",
                "Владелец одобрил вашу заявку на «" + book.getTitle() + "»." +
                        (response != null && !response.isBlank() ? " Ответ: " + response : ""),
                "/"
        );

        ra.addFlashAttribute("success",
                "Бронь одобрена для @" + booking.getRequester().getUsername() + ". Книга отмечена как «Забронирована».");
        return "redirect:/my-books";
    }

    // ── Отклонить бронь ──────────────────────────────────────────────

    @Transactional
    @PostMapping("/{id}/reject")
    public String rejectBooking(@PathVariable Long id,
                                @RequestParam(required = false) String response,
                                Principal principal, RedirectAttributes ra) {
        Booking booking = findPendingForOwner(id, principal.getName());
        if (booking == null) {
            ra.addFlashAttribute("error", "Заявка не найдена или уже обработана.");
            return "redirect:/my-books";
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setOwnerResponse(response != null ? response.trim() : null);
        booking.setRespondedAt(LocalDateTime.now());
        bookingRepository.save(booking);

        notificationService.sendNotification(
                booking.getRequester().getUsername(),
                "❌ Бронирование отклонено",
                "Владелец отклонил вашу заявку на «" + booking.getBook().getTitle() + "»." +
                        (response != null && !response.isBlank() ? " Причина: " + response : ""),
                "/"
        );

        ra.addFlashAttribute("info", "Заявка отклонена.");
        return "redirect:/my-books";
    }

    // ── Завершить бронь (владелец отдал книгу) ───────────────────────

    @Transactional
    @PostMapping("/{id}/complete")
    public String completeBooking(@PathVariable Long id,
                                  Principal principal, RedirectAttributes ra) {
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null || !booking.getOwner().getUsername().equals(principal.getName())) {
            ra.addFlashAttribute("error", "Бронь не найдена.");
            return "redirect:/my-books";
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            ra.addFlashAttribute("error", "Бронь не активна.");
            return "redirect:/my-books";
        }

        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        // Книга теперь "Занята" (передана пользователю)
        Book book = booking.getBook();
        book.setStatus(Book.BookStatus.BUSY);
        bookRepository.save(book);

        notificationService.sendNotification(
                booking.getRequester().getUsername(),
                "📦 Книга передана",
                "Владелец подтвердил передачу «" + book.getTitle() + "». Приятного чтения!",
                "/"
        );

        ra.addFlashAttribute("success", "Книга помечена как переданная.");
        return "redirect:/my-books";
    }

    // ── Отменить свой запрос ─────────────────────────────────────────

    @Transactional
    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable Long id, Principal principal, RedirectAttributes ra) {
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null || !booking.getRequester().getUsername().equals(principal.getName())) {
            ra.addFlashAttribute("error", "Заявка не найдена.");
            return "redirect:/";
        }

        boolean wasAccepted = booking.getStatus() == BookingStatus.ACCEPTED;
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        // Если бронь была одобрена — возвращаем книгу в СВОБОДНА
        if (wasAccepted) {
            Book book = booking.getBook();
            if (book.getStatus() == Book.BookStatus.BOOKED) {
                book.setStatus(Book.BookStatus.FREE);
                bookRepository.save(book);
            }
        }

        ra.addFlashAttribute("success", "Заявка отменена.");
        return "redirect:/";
    }

    // ── API: активная бронь книги ────────────────────────────────────

    @GetMapping("/book/{bookId}/active")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getActiveBooking(@PathVariable Long bookId) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) return ResponseEntity.notFound().build();

        return bookingRepository.findActiveBookingForBook(book, BookingStatus.ACCEPTED)
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("requester", b.getRequester().getUsername());
                    m.put("requestedAt", b.getRequestedAt().toLocalDate().toString());
                    m.put("bookedUntil", b.getBookedUntil() != null
                            ? b.getBookedUntil().toLocalDate().toString() : null);
                    return ResponseEntity.ok(m);
                }).orElse(ResponseEntity.ok(null));
    }

    private Booking findPendingForOwner(Long id, String username) {
        return bookingRepository.findById(id)
                .filter(b -> b.getOwner().getUsername().equals(username)
                        && b.getStatus() == BookingStatus.PENDING)
                .orElse(null);
    }
}