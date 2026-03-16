package com.bookcrossing.controller;

import com.bookcrossing.dto.ConversationDTO;
import com.bookcrossing.model.Message;
import com.bookcrossing.model.User;
import com.bookcrossing.service.ChatService;
import com.bookcrossing.service.NotificationService;
import com.bookcrossing.service.UserService;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    public ChatController(ChatService chatService,
                          UserService userService,
                          SimpMessagingTemplate messagingTemplate,
                          NotificationService notificationService) {
        this.chatService         = chatService;
        this.userService         = userService;
        this.messagingTemplate   = messagingTemplate;
        this.notificationService = notificationService;
    }

    @GetMapping("/messages")
    public String messagesPage(Model model, Principal principal,
                               @RequestParam(required = false) Long partnerId) {
        User currentUser = userService.findByUsername(principal.getName());

        // ИСПРАВЛЕНИЕ: сначала помечаем сообщения прочитанными, и только потом
        // запрашиваем список диалогов — иначе ConversationDTO возвращает
        // устаревший unreadCount и счётчик не сбрасывается на странице.
        if (partnerId != null && !currentUser.getId().equals(partnerId)) {
            User partner = userService.findById(partnerId);
            chatService.markMessagesAsRead(currentUser, partner);
            model.addAttribute("activePartner", partner);
            model.addAttribute("history",
                    chatService.getChatHistory(currentUser.getId(), partnerId));
        }

        List<ConversationDTO> conversations = chatService.getUserConversations(currentUser);
        model.addAttribute("conversations", conversations);
        model.addAttribute("currentUser",   currentUser);

        return "messages";
    }

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Map<String, String> payload, Principal principal) {
        String content     = payload.get("content");
        Long   recipientId = Long.parseLong(payload.get("recipientId"));
        User   sender      = userService.findByUsername(principal.getName());

        Message savedMsg = chatService.saveMessage(sender.getId(), recipientId, content);

        messagingTemplate.convertAndSendToUser(
                savedMsg.getRecipient().getUsername(),
                "/queue/messages",
                savedMsg
        );

        messagingTemplate.convertAndSendToUser(
                sender.getUsername(),
                "/queue/messages",
                savedMsg
        );

        String preview = content.length() > 50
                ? content.substring(0, 50) + "…"
                : content;

        notificationService.sendNotification(
                savedMsg.getRecipient().getUsername(),
                "Новое сообщение от " + sender.getUsername(),
                preview,
                "/messages?partnerId=" + sender.getId()
        );
    }
}