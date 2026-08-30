package com.chat.chatsummarizer.repository;

import com.chat.chatsummarizer.model.ContactRequest;
import com.chat.chatsummarizer.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ContactRequestRepository extends JpaRepository<ContactRequest, Long> {
    List<ContactRequest> findByReceiverAndStatus(User receiver, ContactRequest.Status status);
    List<ContactRequest> findBySenderOrReceiver(User sender, User receiver);
    Optional<ContactRequest> findBySenderAndReceiver(User sender, User receiver);
}
