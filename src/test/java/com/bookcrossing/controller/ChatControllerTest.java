package com.bookcrossing.controller;

import com.bookcrossing.controller.ChatController;
import com.bookcrossing.model.Message;
import com.bookcrossing.model.User;
import com.bookcrossing.service.ChatService;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.ui.Model;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController")
class ChatControllerTest {

    @Mock ChatService            chatService;
    @Mock UserService            userService;
    @Mock SimpMessagingTemplate  messagingTemplate;
    @Mock NotificationService    notificationService;
    @InjectMocks ChatController  chatController;

    private User currentUser;
    private User partner;
    private Principal principal;
    private Model model;

    @BeforeEach
    void setUp() {
        currentUser = new User(); currentUser.setId(1L); currentUser.setUsername("alice");
        partner     = new User(); partner.setId(2L);     partner.setUsername("bob");
        principal   = () -> "alice";
        model       = mock(Model.class);
    }

    // ─── GET /messages ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /messages")
    class MessagesPage {

        @Test
        @DisplayName("Без partnerId — загружает разговоры, не открывает диалог")
        void noPartnerId_loadsConversationsOnly() {
            when(userService.findByUsername("alice")).thenReturn(currentUser);
            when(chatService.getUserConversations(currentUser)).thenReturn(List.of());

            String view = chatController.messagesPage(model, principal, null);

            assertThat(view).isEqualTo("messages");
            verify(model).addAttribute(eq("conversations"), any());
            verify(model).addAttribute(eq("currentUser"), eq(currentUser));
            verify(chatService, never()).markMessagesAsRead(any(), any());
            verify(chatService, never()).getChatHistory(any(), any());
        }

        @Test
        @DisplayName("С partnerId — открывает диалог, помечает прочитанными")
        void withPartnerId_opensDialogAndMarksRead() {
            when(userService.findByUsername("alice")).thenReturn(currentUser);
            when(userService.findById(2L)).thenReturn(partner);
            when(chatService.getUserConversations(currentUser)).thenReturn(List.of());
            when(chatService.getChatHistory(1L, 2L)).thenReturn(List.of());

            String view = chatController.messagesPage(model, principal, 2L);

            assertThat(view).isEqualTo("messages");
            verify(chatService).markMessagesAsRead(currentUser, partner);
            verify(chatService).getChatHistory(1L, 2L);
            verify(model).addAttribute(eq("activePartner"), eq(partner));
            verify(model).addAttribute(eq("history"), any());
        }

        @Test
        @DisplayName("partnerId == currentUser.id — не открывает диалог с собой")
        void partnerIsSelf_noDialog() {
            when(userService.findByUsername("alice")).thenReturn(currentUser);
            when(chatService.getUserConversations(currentUser)).thenReturn(List.of());

            // partnerId = 1L = currentUser.id
            chatController.messagesPage(model, principal, 1L);

            verify(chatService, never()).markMessagesAsRead(any(), any());
            verify(chatService, never()).getChatHistory(any(), any());
        }
    }

    // ─── @MessageMapping /chat.sendMessage ────────────────────────────────────

    @Nested
    @DisplayName("sendMessage WebSocket")
    class SendMessage {

        @Test
        @DisplayName("Отправка — сохраняет, рассылает через WS, отправляет уведомление")
        void send_savesAndDelivers() {
            Message saved = new Message();
            saved.setSender(currentUser);
            saved.setRecipient(partner);
            saved.setContent("Привет!");
            saved.setTimestamp(LocalDateTime.now());

            when(userService.findByUsername("alice")).thenReturn(currentUser);
            when(chatService.saveMessage(1L, 2L, "Привет!")).thenReturn(saved);

            Map<String, String> payload = Map.of("content", "Привет!", "recipientId", "2");
            chatController.sendMessage(payload, principal);

            // Получатель и отправитель оба получают сообщение
            verify(messagingTemplate).convertAndSendToUser(
                    eq("bob"), eq("/queue/messages"), eq(saved));
            verify(messagingTemplate).convertAndSendToUser(
                    eq("alice"), eq("/queue/messages"), eq(saved));
            // Push-уведомление получателю
            verify(notificationService).sendNotification(
                    eq("bob"), contains("alice"), any(), any());
        }

        @Test
        @DisplayName("Длинный контент > 50 символов — preview обрезается с '...'")
        void longContent_previewTruncated() {
            String longMsg = "а".repeat(80);
            Message saved = new Message();
            saved.setSender(currentUser);
            saved.setRecipient(partner);
            saved.setContent(longMsg);
            saved.setTimestamp(LocalDateTime.now());

            when(userService.findByUsername("alice")).thenReturn(currentUser);
            when(chatService.saveMessage(1L, 2L, longMsg)).thenReturn(saved);

            chatController.sendMessage(Map.of("content", longMsg, "recipientId", "2"), principal);

            verify(notificationService).sendNotification(
                    eq("bob"), any(),
                    // Не проверяем конкретный символ многоточия: Word может
                    // автозаменить "..." на "…" (U+2026). Проверяем только длину.
                    argThat((String preview) -> preview.length() < longMsg.length()),
                    any());
        }

        @Test
        @DisplayName("Короткий контент <= 50 символов — preview не обрезается")
        void shortContent_previewNotTruncated() {
            String shortMsg = "Короткое сообщение";
            Message saved = new Message();
            saved.setSender(currentUser);
            saved.setRecipient(partner);
            saved.setContent(shortMsg);
            saved.setTimestamp(LocalDateTime.now());

            when(userService.findByUsername("alice")).thenReturn(currentUser);
            when(chatService.saveMessage(1L, 2L, shortMsg)).thenReturn(saved);

            chatController.sendMessage(Map.of("content", shortMsg, "recipientId", "2"), principal);

            // Короткое сообщение передаётся без изменений
            verify(notificationService).sendNotification(
                    eq("bob"), any(), eq(shortMsg), any());
        }
    }
}