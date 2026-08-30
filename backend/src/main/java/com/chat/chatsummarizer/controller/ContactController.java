package com.chat.chatsummarizer.controller;

import com.chat.chatsummarizer.model.*;
import com.chat.chatsummarizer.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/contacts")
public class ContactController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ContactRequestRepository contactRequestRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    // Search users by username or email (excluding yourself)
    @GetMapping("/search")
    public List<Map<String, Object>> searchUsers(@RequestParam String query, @RequestParam String myEmail) {
        return userRepository.findAll().stream()
                .filter(u -> !u.getEmail().equalsIgnoreCase(myEmail))
                .filter(u -> u.getName().toLowerCase().contains(query.toLowerCase())
                        || u.getEmail().toLowerCase().contains(query.toLowerCase()))
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("username", u.getName());
                    m.put("email", u.getEmail());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // Send a contact request
    @PostMapping("/request")
    public Map<String, Object> sendRequest(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        Optional<User> senderOpt = userRepository.findByEmail(body.get("myEmail"));
        Optional<User> receiverOpt = userRepository.findByEmail(body.get("targetEmail"));

        if (senderOpt.isEmpty() || receiverOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "User not found.");
            return result;
        }

        User sender = senderOpt.get();
        User receiver = receiverOpt.get();

        if (contactRequestRepository.findBySenderAndReceiver(sender, receiver).isPresent()) {
            result.put("success", false);
            result.put("message", "Request already sent.");
            return result;
        }

        contactRequestRepository.save(new ContactRequest(sender, receiver));
        result.put("success", true);
        return result;
    }

    // List pending requests received by me
    @GetMapping("/pending")
    public List<Map<String, Object>> pendingRequests(@RequestParam String myEmail) {
        Optional<User> meOpt = userRepository.findByEmail(myEmail);
        if (meOpt.isEmpty()) return new ArrayList<>();

        return contactRequestRepository.findByReceiverAndStatus(meOpt.get(), ContactRequest.Status.PENDING)
                .stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("requestId", r.getId());
                    m.put("fromUsername", r.getSender().getName());
                    m.put("fromEmail", r.getSender().getEmail());
                    return m;
                })
                .collect(Collectors.toList());
    }

    // Accept a contact request -> creates a Conversation between the two users
    @PostMapping("/accept")
    public Map<String, Object> acceptRequest(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        Long requestId = Long.valueOf(body.get("requestId").toString());

        Optional<ContactRequest> reqOpt = contactRequestRepository.findById(requestId);
        if (reqOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Request not found.");
            return result;
        }

        ContactRequest req = reqOpt.get();
        req.setStatus(ContactRequest.Status.ACCEPTED);
        contactRequestRepository.save(req);

        Conversation conversation = new Conversation();
        conversation = conversationRepository.save(conversation);

        conversationMemberRepository.save(new ConversationMember(conversation, req.getSender()));
        conversationMemberRepository.save(new ConversationMember(conversation, req.getReceiver()));

        result.put("success", true);
        result.put("conversationId", conversation.getId());
        return result;
    }

    // List my accepted contacts + their conversation IDs
    @GetMapping("/list")
    public List<Map<String, Object>> myContacts(@RequestParam String myEmail) {
        Optional<User> meOpt = userRepository.findByEmail(myEmail);
        if (meOpt.isEmpty()) return new ArrayList<>();
        User me = meOpt.get();

        List<Map<String, Object>> results = new ArrayList<>();
        List<ConversationMember> myMemberships = conversationMemberRepository.findByUser(me);

        for (ConversationMember cm : myMemberships) {
            Long convId = cm.getConversation().getId();
            List<ConversationMember> members = conversationMemberRepository.findByConversationId(convId);
            for (ConversationMember other : members) {
                if (!other.getUser().getEmail().equalsIgnoreCase(myEmail)) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("conversationId", convId);
                    m.put("username", other.getUser().getName());
                    m.put("email", other.getUser().getEmail());
                    results.add(m);
                }
            }
        }
        return results;
    }
}