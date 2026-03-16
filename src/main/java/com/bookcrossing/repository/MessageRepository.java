package com.bookcrossing.repository;

import com.bookcrossing.model.Message;
import com.bookcrossing.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // Найти переписку между двумя пользователями
    @Query("SELECT m FROM Message m WHERE (m.sender = :user1 AND m.recipient = :user2) " +
            "OR (m.sender = :user2 AND m.recipient = :user1) ORDER BY m.timestamp ASC")
    List<Message> findChatHistory(@Param("user1") User user1, @Param("user2") User user2);

    // Найти все сообщения пользователя (для формирования списка диалогов)
    @Query("SELECT m FROM Message m WHERE m.sender = :user OR m.recipient = :user ORDER BY m.timestamp DESC")
    List<Message> findAllByUser(@Param("user") User user);

    // Количество непрочитанных сообщений от конкретного отправителя
    long countByRecipientAndSenderAndReadFalse(User recipient, User sender);
    List<Message> findByRecipientAndSenderAndReadFalse(User recipient, User sender);

    @Modifying
    @Query("UPDATE Message m SET m.read = true " +
            "WHERE m.recipient = :recipient AND m.sender = :sender AND m.read = false")
    void markMessagesAsRead(@Param("recipient") User recipient,
                            @Param("sender") User sender);
}