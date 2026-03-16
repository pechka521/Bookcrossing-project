package com.bookcrossing.repository;

import com.bookcrossing.model.Book;
import com.bookcrossing.model.Booking;
import com.bookcrossing.model.Booking.BookingStatus;
import com.bookcrossing.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByBookAndStatus(Book book, BookingStatus status);

    List<Booking> findByRequesterOrderByRequestedAtDesc(User requester);

    /**
     * ИСПРАВЛЕНИЕ LazyInitializationException на странице «Мои книги»:
     * Booking.book и Booking.requester — FetchType.LAZY.
     * findByOwnerAndStatusOrderByRequestedAtDesc без JOIN FETCH возвращал
     * прокси-объекты. Thymeleaf пытался обратиться к booking.book.title
     * после закрытия JPA-сессии → LazyInitializationException → 500.
     * JOIN FETCH загружает все нужные данные за один SQL-запрос.
     */
    @Query("SELECT b FROM Booking b " +
            "JOIN FETCH b.book " +
            "JOIN FETCH b.requester " +
            "WHERE b.owner = :owner AND b.status = :status " +
            "ORDER BY b.requestedAt DESC")
    List<Booking> findByOwnerAndStatusOrderByRequestedAtDesc(
            @Param("owner") User owner,
            @Param("status") BookingStatus status);

    boolean existsByBookAndRequesterAndStatus(Book book, User requester,
                                              BookingStatus status);

    // Hibernate 6: передаём enum как параметр (строковые литералы не поддерживаются)
    @Query("SELECT b FROM Booking b WHERE b.book = :book AND b.status = :status")
    Optional<Booking> findActiveBookingForBook(
            @Param("book") Book book,
            @Param("status") BookingStatus status);

    long countByOwnerAndStatus(User owner, BookingStatus status);
}