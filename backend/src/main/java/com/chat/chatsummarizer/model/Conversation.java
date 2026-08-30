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

    public Conversation() {}

    public Long getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
