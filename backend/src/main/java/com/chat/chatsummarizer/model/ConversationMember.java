package com.chat.chatsummarizer.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "conversation_member")
public class ConversationMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    @Column
    private LocalDateTime lastReadAt;
    public ConversationMember() {}
    public ConversationMember(Conversation conversation, User user) {
        this.conversation = conversation;
        this.user = user;
        this.lastReadAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getUser() { return user; }
    public LocalDateTime getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(LocalDateTime lastReadAt) { this.lastReadAt = lastReadAt; }
}