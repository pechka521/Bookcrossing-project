package com.bookcrossing.controller;

import com.bookcrossing.model.Book;
import com.bookcrossing.model.BookGenre;
import com.bookcrossing.model.Booking;
import com.bookcrossing.model.Booking.BookingStatus;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.BookingRepository;
import com.bookcrossing.repository.ReviewRepository;
import com.bookcrossing.service.AchievementService;
import com.bookcrossing.service.BookService;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class BookController {

    private final BookService         bookService;
    private final UserService         userService;
    private final NotificationService notificationService;
    private final ReviewRepository    reviewRepository;
    private final BookingRepository   bookingRepository;
    private final AchievementService  achievementService;

    public BookController(BookService bookService,
                          UserService userService,
                          NotificationService notificationService,
                          ReviewRepository reviewRepository,
                          BookingRepository bookingRepository,
                          AchievementService achievementService) {
        this.bookService         = bookService;
        this.userService         = userService;
        this.notificationService = notificationService;
        this.reviewRepository    = reviewRepository;
        this.bookingRepository   = bookingRepository;
        this.achievementService  = achievementService;
    }

    @GetMapping("/")
    public String catalog(Model model,
                          @RequestParam(required = false) String query,
                          @RequestParam(required = false) String genre) {
        List<Book> books = bookService.searchBooks(query, genre);
        Map<Long, Double> ratings = new HashMap<>();
        Map<Long, Long>   counts  = new HashMap<>();
        for (Book book : books) {
            ratings.put(book.getId(), reviewRepository.findAverageRatingByBookId(book.getId()));
            counts .put(book.getId(), reviewRepository.countByBookId(book.getId()));
        }
        model.addAttribute("books",        books);
        model.addAttribute("ratings",      ratings);
        model.addAttribute("reviewCounts", counts);
        model.addAttribute("genres",       BookGenre.values());
        model.addAttribute("selectedGenre", genre);
        model.addAttribute("searchQuery",  query);
        return "catalog";
    }

    @GetMapping("/my-books")
    public String myBooks(Model model, Principal principal,
                          @RequestParam(required = false) String query,
                          @RequestParam(required = false) String genre) {
        User user = userService.findByUsername(principal.getName());
        model.addAttribute("books",          bookService.getMyBooks(user, query, genre));
        model.addAttribute("genres",         BookGenre.values());
        model.addAttribute("selectedGenre",  genre);
        model.addAttribute("searchQuery",    query);
        // Входящие заявки (ждут одобрения)
        model.addAttribute("pendingBookings",
                bookingRepository.findByOwnerAndStatusOrderByRequestedAtDesc(
                        user, BookingStatus.PENDING));
        // Одобренные брони (книга забронирована, ещё не передана)
        model.addAttribute("acceptedBookings",
                bookingRepository.findByOwnerAndStatusOrderByRequestedAtDesc(
                        user, BookingStatus.ACCEPTED));
        return "my-books";
    }

    @GetMapping("/add-book")
    public String addBookForm(Model model) {
        model.addAttribute("book",   new Book());
        model.addAttribute("genres", BookGenre.values());
        return "add-book";
    }

    @PostMapping("/add-book")
    public String addBook(@Valid @ModelAttribute Book book,
                          BindingResult bindingResult,
                          @RequestParam("coverFile") MultipartFile file,
                          Principal principal, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("genres", BookGenre.values());
            return "add-book";
        }
        User user = userService.findByUsername(principal.getName());
        bookService.saveBook(book, user, file);
        achievementService.checkAndAward(user);
        return "redirect:/my-books";
    }

    @PostMapping("/book/{id}/status")
    public String toggleStatus(@PathVariable Long id, Principal principal) {
        User user = userService.findByUsername(principal.getName());
        Book book = bookService.toggleStatus(id, user);
        if (book != null) {
            String statusText = book.getStatus() == Book.BookStatus.FREE ? "свободна" : "занята";
            notificationService.sendNotification(
                    user.getUsername(),
                    "Статус книги изменён",
                    "«" + book.getTitle() + "» теперь " + statusText,
                    "/my-books"
            );
        }
        achievementService.checkAndAward(user);
        return "redirect:/my-books";
    }

    @PostMapping("/book/{id}/delete")
    public String deleteBook(@PathVariable Long id,
                             Principal principal,
                             RedirectAttributes ra) {
        User user = userService.findByUsername(principal.getName());
        boolean deleted = bookService.deleteBook(id, user);
        if (deleted) ra.addFlashAttribute("successMessage", "Книга успешно удалена.");
        else         ra.addFlashAttribute("errorMessage",   "Не удалось удалить книгу.");
        return "redirect:/my-books";
    }
}