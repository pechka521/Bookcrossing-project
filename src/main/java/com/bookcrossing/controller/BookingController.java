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

    /** Максимальный срок бронирования — 90 дней (≈ 3 месяца) (#1) */
    private static final int MAX_BOOKING_DAYS = 90;

    private final BookingRepository bookingRepository;
    private final BookRepository bookRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    public BookingController(BookingRepository bookingRepository,
                             BookRepository bookRepository,
                             UserService userService,
                             NotificationService notificationService) {
        this.bookingRepository = bookingRepository;
        this.bookRepository = bookRepository;
        this.userService = userService;
        this.notificationService = notificationService;
    }

    /** Запрос на бронирование от читателя */
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

        // (#1) Ограничиваем срок максимумом 90 дней
        if (days != null && days > MAX_BOOKING_DAYS) {
            ra.addFlashAttribute("error",
                    "Максимальный срок бронирования — " + MAX_BOOKING_DAYS + " дней (3 месяца).");
            return "redirect:/";
        }

        Booking booking = new Booking();
        booking.setBook(book);
        booking.setRequester(requester);
        booking.setOwner(book.getOwner());
        booking.setStatus(BookingStatus.PENDING);
        if (message != null && !message.isBlank()) booking.setMessage(message.trim());
        if (days != null && days > 0)
            booking.setBookedUntil(LocalDateTime.now().plusDays(days));
        bookingRepository.save(booking);

        notificationService.sendNotification(
                book.getOwner().getUsername(),
                "📚 Запрос на бронирование",
                "@" + requester.getUsername() + " хочет забронировать «" + book.getTitle() + "»." +
                        (message != null && !message.isBlank() ? " Сообщение: " + message : ""),
                "/my-books"
        );

        ra.addFlashAttribute("success", "Запрос отправлен! Ждите ответа от владельца.");
        return "redirect:/";
    }

    /** Владелец одобряет заявку → книга BOOKED */
    @Transactional
    @PostMapping("/{id}/accept")
    public String acceptBooking(@PathVariable Long id,
                                @RequestParam(required = false) String response,
                                Principal principal, RedirectAttributes ra) {
        Booking booking = findPendingForOwner(id, principal.getName());
        if (booking == null) {
            ra.addFlashAttribute("errorMessage", "Заявка не найдена или уже обработана.");
            return "redirect:/my-books";
        }
        booking.setStatus(BookingStatus.ACCEPTED);
        booking.setOwnerResponse(response != null ? response.trim() : null);
        booking.setRespondedAt(LocalDateTime.now());
        bookingRepository.save(booking);

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

        ra.addFlashAttribute("successMessage",
                "Бронь одобрена для @" + booking.getRequester().getUsername() +
                        ". Книга отмечена как «Забронирована».");
        return "redirect:/my-books";
    }

    /** Владелец отклоняет заявку */
    @Transactional
    @PostMapping("/{id}/reject")
    public String rejectBooking(@PathVariable Long id,
                                @RequestParam(required = false) String response,
                                Principal principal, RedirectAttributes ra) {
        Booking booking = findPendingForOwner(id, principal.getName());
        if (booking == null) {
            ra.addFlashAttribute("errorMessage", "Заявка не найдена или уже обработана.");
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

        ra.addFlashAttribute("successMessage", "Заявка отклонена.");
        return "redirect:/my-books";
    }

    /** Владелец подтверждает передачу книги → книга BUSY */
    @Transactional
    @PostMapping("/{id}/complete")
    public String completeBooking(@PathVariable Long id,
                                  Principal principal, RedirectAttributes ra) {
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null || !booking.getOwner().getUsername().equals(principal.getName())) {
            ra.addFlashAttribute("errorMessage", "Бронь не найдена.");
            return "redirect:/my-books";
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            ra.addFlashAttribute("errorMessage", "Бронь не активна.");
            return "redirect:/my-books";
        }
        booking.setStatus(BookingStatus.COMPLETED);
        bookingRepository.save(booking);

        Book book = booking.getBook();
        book.setStatus(Book.BookStatus.BUSY);
        bookRepository.save(book);

        notificationService.sendNotification(
                booking.getRequester().getUsername(),
                "📦 Книга передана",
                "Владелец подтвердил передачу «" + book.getTitle() + "». Приятного чтения!",
                "/"
        );

        ra.addFlashAttribute("successMessage", "Книга помечена как переданная.");
        return "redirect:/my-books";
    }

    /** Владелец отменяет одобренную бронь → книга FREE */
    @Transactional
    @PostMapping("/{id}/release")
    public String releaseBooking(@PathVariable Long id,
                                 Principal principal, RedirectAttributes ra) {
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null || !booking.getOwner().getUsername().equals(principal.getName())) {
            ra.addFlashAttribute("errorMessage", "Бронь не найдена.");
            return "redirect:/my-books";
        }
        if (booking.getStatus() != BookingStatus.ACCEPTED) {
            ra.addFlashAttribute("errorMessage", "Бронь не активна.");
            return "redirect:/my-books";
        }
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

        Book book = booking.getBook();
        book.setStatus(Book.BookStatus.FREE);
        bookRepository.save(book);

        notificationService.sendNotification(
                booking.getRequester().getUsername(),
                "❌ Бронь отменена владельцем",
                "Владелец отменил вашу бронь на «" + book.getTitle() + "». Книга снова свободна.",
                "/"
        );

        ra.addFlashAttribute("successMessage",
                "Бронь отменена. Книга «" + book.getTitle() + "» снова свободна.");
        return "redirect:/my-books";
    }

    /** Читатель отменяет свою заявку */
    @Transactional
    @PostMapping("/{id}/cancel")
    public String cancelBooking(@PathVariable Long id,
                                Principal principal, RedirectAttributes ra) {
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null || !booking.getRequester().getUsername().equals(principal.getName())) {
            ra.addFlashAttribute("error", "Заявка не найдена.");
            return "redirect:/";
        }
        boolean wasAccepted = booking.getStatus() == BookingStatus.ACCEPTED;
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);

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

    @GetMapping("/book/{bookId}/active")
    @ResponseBody
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getActiveBooking(@PathVariable Long bookId) {
        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) return ResponseEntity.notFound().build();

        return bookingRepository.findActiveBookingForBook(book, BookingStatus.ACCEPTED)
                .map(b -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("requester",   b.getRequester().getUsername());
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