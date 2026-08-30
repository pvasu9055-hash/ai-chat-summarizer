package com.chat.chatsummarizer.repository;

import com.chat.chatsummarizer.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
}
