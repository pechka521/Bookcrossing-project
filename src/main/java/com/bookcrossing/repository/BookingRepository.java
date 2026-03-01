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

    List<Booking> findByOwnerAndStatusOrderByRequestedAtDesc(User owner, BookingStatus status);

    boolean existsByBookAndRequesterAndStatus(Book book, User requester, BookingStatus status);

    // Hibernate 6: передаём enum как параметр — строковые литералы и полные пути не поддерживаются
    @Query("SELECT b FROM Booking b WHERE b.book = :book AND b.status = :status")
    Optional<Booking> findActiveBookingForBook(
            @Param("book") Book book,
            @Param("status") BookingStatus status);

    long countByOwnerAndStatus(User owner, BookingStatus status);
}