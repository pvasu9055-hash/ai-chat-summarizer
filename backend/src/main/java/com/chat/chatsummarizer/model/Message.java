package com.chat.chatsummarizer.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "message")
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;
    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;
    @Column(nullable = false, length = 1000)
    private String content;
    @Column(nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();
    @Column(nullable = false)
    private boolean deletedForEveryone = false;
    @Column(length = 2000)
    private String hiddenFor = "";
    public Message() {}
    public Message(Conversation conversation, User sender, String content) {
        this.conversation = conversation;
        this.sender = sender;
        this.content = content;
    }
    public Long getId() { return id; }
    public Conversation getConversation() { return conversation; }
    public User getSender() { return sender; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getSentAt() { return sentAt; }
    public boolean isDeletedForEveryone() { return deletedForEveryone; }
    public void setDeletedForEveryone(boolean deletedForEveryone) { this.deletedForEveryone = deletedForEveryone; }
    public String getHiddenFor() { return hiddenFor; }
    public void setHiddenFor(String hiddenFor) { this.hiddenFor = hiddenFor; }
}