package com.chat.chatsummarizer.repository;
import com.chat.chatsummarizer.model.Conversation;
import com.chat.chatsummarizer.model.ConversationMember;
import com.chat.chatsummarizer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {
    List<ConversationMember> findByUser(User user);
    List<ConversationMember> findByConversationId(Long conversationId);
    Optional<ConversationMember> findByConversationAndUser(Conversation conversation, User user);
}