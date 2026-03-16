package com.bookcrossing.service;

import com.bookcrossing.model.Book;
import com.bookcrossing.model.Notification;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

    @Mock JavaMailSender mailSender;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationService notificationService;

    private Notification savedNotification;

    @BeforeEach
    void setUp() {
        savedNotification = new Notification();
        savedNotification.setId(42L);
        savedNotification.setUsername("alice");
        savedNotification.setRead(false);
    }

    // ─── sendNotification ────────────────────────────────────────────────────

    @Test
    @DisplayName("sendNotification — сохраняет в БД с правильными полями")
    void sendNotification_savesWithCorrectFields() {
        when(notificationRepository.save(any())).thenReturn(savedNotification);

        notificationService.sendNotification("alice", "Заголовок", "Текст", "/link");

        ArgumentCaptor<Notification> cap = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(cap.capture());
        Notification saved = cap.getValue();
        assertThat(saved.getUsername()).isEqualTo("alice");
        assertThat(saved.getTitle()).isEqualTo("Заголовок");
        assertThat(saved.getBody()).isEqualTo("Текст");
        assertThat(saved.getLink()).isEqualTo("/link");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("sendNotification — отправляет WebSocket сообщение")
    void sendNotification_sendsWebSocket() {
        when(notificationRepository.save(any())).thenReturn(savedNotification);

        notificationService.sendNotification("alice", "T", "B", "/l");

        verify(messagingTemplate).convertAndSendToUser(
                eq("alice"),
                eq("/queue/notifications"),
                argThat(payload -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) payload;
                    return map.containsKey("id")
                            && map.get("title").equals("T")
                            && map.get("body").equals("B");
                })
        );
    }

    // ─── getNotifications ────────────────────────────────────────────────────

    @Test
    @DisplayName("getNotifications — делегирует в repository")
    void getNotifications_delegatesToRepository() {
        when(notificationRepository.findByUsernameOrderByCreatedAtDesc("alice"))
                .thenReturn(List.of(savedNotification));

        List<Notification> result = notificationService.getNotifications("alice");

        assertThat(result).containsExactly(savedNotification);
    }

    // ─── getUnreadCount ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getUnreadCount — возвращает число из repository")
    void getUnreadCount_returnsRepositoryValue() {
        when(notificationRepository.countByUsernameAndReadFalse("alice")).thenReturn(5L);
        assertThat(notificationService.getUnreadCount("alice")).isEqualTo(5L);
    }

    // ─── markAllRead ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("markAllRead — вызывает repository")
    void markAllRead_callsRepository() {
        notificationService.markAllRead("alice");
        verify(notificationRepository).markAllReadByUsername("alice");
    }

    // ─── markRead ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("markRead — уведомление найдено — помечает прочитанным")
    void markRead_found_marksRead() {
        savedNotification.setRead(false);
        when(notificationRepository.findById(42L)).thenReturn(Optional.of(savedNotification));

        notificationService.markRead(42L);

        assertThat(savedNotification.isRead()).isTrue();
        verify(notificationRepository).save(savedNotification);
    }

    @Test
    @DisplayName("markRead — уведомление не найдено — ничего не делает")
    void markRead_notFound_noException() {
        when(notificationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> notificationService.markRead(99L));
        verify(notificationRepository, never()).save(any());
    }

    // ─── sendBookingEmail ─────────────────────────────────────────────────────

    @Test
    @DisplayName("sendBookingEmail — отправляет email с правильным адресом")
    void sendBookingEmail_sendsEmailToOwner() {
        User owner  = new User(); owner.setUsername("bob");  owner.setEmail("bob@mail.com");
        User reader = new User(); reader.setUsername("alice");
        Book book   = new Book(); book.setTitle("Война и мир");

        notificationService.sendBookingEmail(owner, reader, book);

        ArgumentCaptor<SimpleMailMessage> cap = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(cap.capture());
        SimpleMailMessage msg = cap.getValue();
        assertThat(msg.getTo()).contains("bob@mail.com");
        assertThat(msg.getSubject()).contains("забронировали");
        assertThat(msg.getText()).contains("alice").contains("Война и мир");
    }

    @Test
    @DisplayName("sendBookingEmail — ошибка mail — не бросает исключение")
    void sendBookingEmail_mailError_noException() {
        User owner  = new User(); owner.setEmail("x@x.com"); owner.setUsername("x");
        User reader = new User(); reader.setUsername("y");
        Book book   = new Book(); book.setTitle("Book");

        doThrow(new RuntimeException("SMTP error")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatNoException().isThrownBy(
                () -> notificationService.sendBookingEmail(owner, reader, book)
        );
    }

    @Test
    @DisplayName("getUnreadCount — возвращает количество непрочитанных")
    void getUnreadCount_returnsCount() {
        when(notificationRepository.countByUsernameAndReadFalse("alice")).thenReturn(5L);
        assertThat(notificationService.getUnreadCount("alice")).isEqualTo(5L);
    }

    @Test
    @DisplayName("getNotifications — делегирует в репозиторий")
    void getNotifications_delegates() {
        com.bookcrossing.model.Notification n = new com.bookcrossing.model.Notification();
        when(notificationRepository.findByUsernameOrderByCreatedAtDesc("alice"))
                .thenReturn(java.util.List.of(n));
        assertThat(notificationService.getNotifications("alice")).containsExactly(n);
    }

}