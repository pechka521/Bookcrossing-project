package com.bookcrossing.service;

import com.bookcrossing.dto.ConversationDTO;
import com.bookcrossing.model.Message;
import com.bookcrossing.model.User;
import com.bookcrossing.repository.MessageRepository;
import com.bookcrossing.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public ChatService(MessageRepository messageRepository, UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    public Message saveMessage(Long senderId, Long recipientId, String content) {
        User sender = userRepository.findById(senderId).orElseThrow();
        User recipient = userRepository.findById(recipientId).orElseThrow();

        Message message = new Message();
        message.setSender(sender);
        message.setRecipient(recipient);
        message.setContent(content);
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);

        return messageRepository.save(message);
    }

    public List<Message> getChatHistory(Long userId1, Long userId2) {
        User user1 = userRepository.findById(userId1).orElseThrow();
        User user2 = userRepository.findById(userId2).orElseThrow();
        return messageRepository.findChatHistory(user1, user2);
    }

    // Сложная логика для получения списка уникальных диалогов
    public List<ConversationDTO> getUserConversations(User currentUser) {
        List<Message> allMessages = messageRepository.findAllByUser(currentUser);
        Map<Long, ConversationDTO> conversations = new HashMap<>();

        for (Message m : allMessages) {
            User partner = m.getSender().equals(currentUser) ? m.getRecipient() : m.getSender();

            // Если диалог с этим партнером уже обработан (мы идем от новых к старым), пропускаем
            if (!conversations.containsKey(partner.getId())) {
                long unread = messageRepository.countByRecipientAndSenderAndReadFalse(currentUser, partner);
                conversations.put(partner.getId(), new ConversationDTO(
                        partner,
                        m.getContent(),
                        m.getTimestamp(),
                        unread
                ));
            }
        }
        return new ArrayList<>(conversations.values());
    }

    @Transactional
    public void markMessagesAsRead(User recipient, User sender) {
        messageRepository.markMessagesAsRead(recipient, sender);
    }
}