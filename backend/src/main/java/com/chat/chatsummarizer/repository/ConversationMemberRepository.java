package com.chat.chatsummarizer.repository;

import com.chat.chatsummarizer.model.ConversationMember;
import com.chat.chatsummarizer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {
    List<ConversationMember> findByUser(User user);
    List<ConversationMember> findByConversationId(Long conversationId);
}
