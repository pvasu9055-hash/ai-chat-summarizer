package com.chat.chatsummarizer.model;
import jakarta.persistence.*;
import java.time.LocalDateTime;
@Entity
@Table(name = "conversation")
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(nullable = false)
    private boolean isGroup = false;
    @Column
    private String name;
    public Conversation() {}
    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isGroup() { return isGroup; }
    public void setGroup(boolean group) { isGroup = group; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
