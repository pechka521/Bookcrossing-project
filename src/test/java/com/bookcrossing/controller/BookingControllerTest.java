package com.bookcrossing.controller;

import com.bookcrossing.model.*;
import com.bookcrossing.model.Booking.BookingStatus;
import com.bookcrossing.repository.BookRepository;
import com.bookcrossing.repository.BookingRepository;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingController")
class BookingControllerTest {

    @Mock BookingRepository   bookingRepository;
    @Mock BookRepository      bookRepository;
    @Mock UserService         userService;
    @Mock NotificationService notificationService;
    @InjectMocks BookingController bookingController;

    private User owner;
    private User requester;
    private Book book;
    private Principal ownerPrincipal;
    private Principal requesterPrincipal;
    private RedirectAttributes ra;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setUsername("owner");

        requester = new User();
        requester.setId(2L);
        requester.setUsername("requester");

        book = new Book();
        book.setId(10L);
        book.setTitle("Test Book");
        book.setOwner(owner);
        book.setStatus(Book.BookStatus.FREE);

        ownerPrincipal     = () -> "owner";
        requesterPrincipal = () -> "requester";
        ra = mock(RedirectAttributes.class);
    }

    // ─── requestBooking ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("requestBooking")
    class RequestBooking {

        @Test
        @DisplayName("Успешный запрос — бронь сохранена, уведомление отправлено")
        void success_saveBookingAndNotify() {
            when(userService.findByUsername("requester")).thenReturn(requester);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(bookingRepository.existsByBookAndRequesterAndStatus(book, requester, BookingStatus.PENDING))
                    .thenReturn(false);

            String redirect = bookingController.requestBooking(10L, "Хочу почитать", 7,
                    requesterPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/");
            ArgumentCaptor<Booking> cap = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(cap.capture());
            Booking saved = cap.getValue();
            assertThat(saved.getRequester()).isEqualTo(requester);
            assertThat(saved.getBook()).isEqualTo(book);
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.PENDING);
            assertThat(saved.getMessage()).isEqualTo("Хочу почитать");
            verify(notificationService).sendNotification(eq("owner"), contains("бронирование"), any(), any());
        }

        @Test
        @DisplayName("Бронирование своей книги — ошибка, redirect:/")
        void ownBook_errorRedirect() {
            when(userService.findByUsername("owner")).thenReturn(owner);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

            String redirect = bookingController.requestBooking(10L, null, null, ownerPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/");
            verify(ra).addFlashAttribute(eq("error"), contains("собственную"));
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Книга не FREE — ошибка")
        void bookNotFree_errorRedirect() {
            book.setStatus(Book.BookStatus.BUSY);
            when(userService.findByUsername("requester")).thenReturn(requester);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));

            String redirect = bookingController.requestBooking(10L, null, null, requesterPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/");
            verify(ra).addFlashAttribute(eq("error"), any());
            verify(bookingRepository, never()).save(any());
        }

        @Test
        @DisplayName("Уже есть PENDING заявка — ошибка")
        void duplicatePending_errorRedirect() {
            when(userService.findByUsername("requester")).thenReturn(requester);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(bookingRepository.existsByBookAndRequesterAndStatus(book, requester, BookingStatus.PENDING))
                    .thenReturn(true);

            String redirect = bookingController.requestBooking(10L, null, null, requesterPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/");
            verify(ra).addFlashAttribute(eq("error"), contains("уже"));
        }

        @Test
        @DisplayName("Без сообщения — message не устанавливается")
        void noMessage_messageNull() {
            when(userService.findByUsername("requester")).thenReturn(requester);
            when(bookRepository.findById(10L)).thenReturn(Optional.of(book));
            when(bookingRepository.existsByBookAndRequesterAndStatus(any(), any(), any())).thenReturn(false);

            bookingController.requestBooking(10L, "   ", null, requesterPrincipal, ra);

            ArgumentCaptor<Booking> cap = ArgumentCaptor.forClass(Booking.class);
            verify(bookingRepository).save(cap.capture());
            assertThat(cap.getValue().getMessage()).isNull();
        }
    }

    // ─── acceptBooking ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("acceptBooking")
    class AcceptBooking {

        @Test
        @DisplayName("Успешное одобрение — статус ACCEPTED, книга BOOKED")
        void success_acceptsAndSetsBookBooked() {
            Booking booking = pendingBooking();
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            String redirect = bookingController.acceptBooking(1L, "Жду вас в среду",
                    ownerPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/my-books");
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.ACCEPTED);
            assertThat(book.getStatus()).isEqualTo(Book.BookStatus.BOOKED);
            verify(bookRepository).save(book);
            verify(notificationService).sendNotification(eq("requester"), contains("одобрено"), any(), any());
        }

        @Test
        @DisplayName("Заявка не найдена — ошибка")
        void notFound_error() {
            when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

            String redirect = bookingController.acceptBooking(99L, null, ownerPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/my-books");
            verify(ra).addFlashAttribute(eq("error"), any());
        }

        @Test
        @DisplayName("Чужой владелец — возвращает ошибку")
        void wrongOwner_error() {
            Booking booking = pendingBooking();
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            String redirect = bookingController.acceptBooking(1L, null, requesterPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/my-books");
            verify(ra).addFlashAttribute(eq("error"), any());
        }
    }

    // ─── rejectBooking ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectBooking")
    class RejectBooking {

        @Test
        @DisplayName("Успешное отклонение — статус REJECTED, уведомление отправлено")
        void success_rejectsBooking() {
            Booking booking = pendingBooking();
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            String redirect = bookingController.rejectBooking(1L, "Уже нашёл читателя",
                    ownerPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/my-books");
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.REJECTED);
            verify(notificationService).sendNotification(eq("requester"), contains("отклонено"), any(), any());
        }

        @Test
        @DisplayName("Не найдена — ошибка")
        void notFound_error() {
            when(bookingRepository.findById(99L)).thenReturn(Optional.empty());
            String redirect = bookingController.rejectBooking(99L, null, ownerPrincipal, ra);
            verify(ra).addFlashAttribute(eq("error"), any());
        }
    }

    // ─── completeBooking ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeBooking")
    class CompleteBooking {

        @Test
        @DisplayName("Успешное завершение — статус COMPLETED, книга BUSY")
        void success_completesAndSetsBusy() {
            Booking booking = acceptedBooking();
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            String redirect = bookingController.completeBooking(1L, ownerPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/my-books");
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
            assertThat(book.getStatus()).isEqualTo(Book.BookStatus.BUSY);
            verify(notificationService).sendNotification(eq("requester"), any(), contains("Приятного чтения"), any());
        }

        @Test
        @DisplayName("Бронь не ACCEPTED — ошибка")
        void notAccepted_error() {
            Booking booking = pendingBooking();
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            String redirect = bookingController.completeBooking(1L, ownerPrincipal, ra);

            verify(ra).addFlashAttribute(eq("error"), any());
        }

        @Test
        @DisplayName("Бронь не найдена — ошибка")
        void notFound_error() {
            when(bookingRepository.findById(99L)).thenReturn(Optional.empty());
            bookingController.completeBooking(99L, ownerPrincipal, ra);
            verify(ra).addFlashAttribute(eq("error"), any());
        }
    }

    // ─── cancelBooking ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancelBooking")
    class CancelBooking {

        @Test
        @DisplayName("Отмена PENDING заявки — статус CANCELLED")
        void cancelPending_success() {
            Booking booking = pendingBooking();
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            String redirect = bookingController.cancelBooking(1L, requesterPrincipal, ra);

            assertThat(redirect).isEqualTo("redirect:/");
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        }

        @Test
        @DisplayName("Отмена ACCEPTED брони — книга возвращается в FREE")
        void cancelAccepted_bookBecomeFree() {
            Booking booking = acceptedBooking();
            book.setStatus(Book.BookStatus.BOOKED);
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            bookingController.cancelBooking(1L, requesterPrincipal, ra);

            assertThat(book.getStatus()).isEqualTo(Book.BookStatus.FREE);
            verify(bookRepository).save(book);
        }

        @Test
        @DisplayName("Чужая заявка — ошибка")
        void wrongUser_error() {
            Booking booking = pendingBooking();
            when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

            bookingController.cancelBooking(1L, ownerPrincipal, ra);  // owner != requester

            verify(ra).addFlashAttribute(eq("error"), any());
        }

        @Test
        @DisplayName("Заявка не найдена — ошибка")
        void notFound_error() {
            when(bookingRepository.findById(99L)).thenReturn(Optional.empty());
            bookingController.cancelBooking(99L, requesterPrincipal, ra);
            verify(ra).addFlashAttribute(eq("error"), any());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Booking pendingBooking() {
        Booking b = new Booking();
        b.setId(1L);
        b.setBook(book);
        b.setOwner(owner);
        b.setRequester(requester);
        b.setStatus(BookingStatus.PENDING);
        return b;
    }

    private Booking acceptedBooking() {
        Booking b = pendingBooking();
        b.setStatus(BookingStatus.ACCEPTED);
        return b;
    }
}