package com.chat.chatsummarizer.controller;

import com.chat.chatsummarizer.model.*;
import com.chat.chatsummarizer.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Controller
public class ChatController {

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private ConversationMemberRepository conversationMemberRepository;

    @Value("${gemini.api.key}")
    private String API_KEY;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=";

    @MessageMapping("/chat/{conversationId}")
    public void sendMessage(@DestinationVariable Long conversationId, Map<String, String> payload) {
        String senderEmail = payload.get("senderEmail");
        String content = payload.get("content");

        Optional<Conversation> convOpt = conversationRepository.findById(conversationId);
        Optional<User> senderOpt = userRepository.findByEmail(senderEmail);

        if (convOpt.isEmpty() || senderOpt.isEmpty()) {
            return;
        }

        Message message = new Message(convOpt.get(), senderOpt.get(), content);
        messageRepository.save(message);

        Map<String, Object> outgoing = new HashMap<>();
        outgoing.put("messageId", message.getId());
        outgoing.put("conversationId", conversationId);
        outgoing.put("senderEmail", senderEmail);
        outgoing.put("senderUsername", senderOpt.get().getName());
        outgoing.put("content", content);
        outgoing.put("sentAt", message.getSentAt().toString());

        messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, outgoing);
    }

    @GetMapping("/messages/{conversationId}")
    @ResponseBody
    public List<Map<String, Object>> getMessages(@PathVariable Long conversationId, @RequestParam String myEmail) {
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId)
                .stream()
                .filter(m -> !isHiddenFor(m, myEmail))
                .map(m -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("messageId", m.getId());
                    map.put("senderEmail", m.getSender().getEmail());
                    map.put("senderUsername", m.getSender().getName());
                    map.put("content", m.isDeletedForEveryone() ? "This message was deleted" : m.getContent());
                    map.put("deletedForEveryone", m.isDeletedForEveryone());
                    map.put("sentAt", m.getSentAt().toString());
                    return map;
                })
                .collect(Collectors.toList());
    }

    @DeleteMapping("/messages/{messageId}")
    @ResponseBody
    public Map<String, Object> deleteMessage(@PathVariable Long messageId,
                                              @RequestParam String myEmail,
                                              @RequestParam boolean forEveryone) {
        Map<String, Object> result = new HashMap<>();
        Optional<Message> msgOpt = messageRepository.findById(messageId);

        if (msgOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "Message not found.");
            return result;
        }

        Message message = msgOpt.get();

        if (forEveryone) {
            if (!message.getSender().getEmail().equalsIgnoreCase(myEmail)) {
                result.put("success", false);
                result.put("message", "Only the sender can delete this for everyone.");
                return result;
            }
            message.setDeletedForEveryone(true);
            messageRepository.save(message);

            Map<String, Object> outgoing = new HashMap<>();
            outgoing.put("type", "delete");
            outgoing.put("messageId", messageId);
            outgoing.put("deletedForEveryone", true);
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + message.getConversation().getId(), outgoing);
        } else {
            List<String> hidden = new ArrayList<>(Arrays.asList(message.getHiddenFor().split(",")));
            hidden.removeIf(String::isBlank);
            if (!hidden.contains(myEmail)) {
                hidden.add(myEmail);
            }
            message.setHiddenFor(String.join(",", hidden));
            messageRepository.save(message);
        }

        result.put("success", true);
        return result;
    }

    @GetMapping("/catchup/{conversationId}")
    @ResponseBody
    public Map<String, Object> catchUp(@PathVariable Long conversationId, @RequestParam String myEmail) {
        Map<String, Object> result = new HashMap<>();
        Optional<Conversation> convOpt = conversationRepository.findById(conversationId);
        Optional<User> meOpt = userRepository.findByEmail(myEmail);

        if (convOpt.isEmpty() || meOpt.isEmpty()) {
            result.put("hasNew", false);
            return result;
        }

        Optional<ConversationMember> memberOpt =
                conversationMemberRepository.findByConversationAndUser(convOpt.get(), meOpt.get());

        java.time.LocalDateTime lastReadAt = memberOpt.map(ConversationMember::getLastReadAt)
                .orElse(java.time.LocalDateTime.now().minusYears(10));

        List<Message> newMessages = messageRepository.findByConversationIdOrderBySentAtAsc(conversationId)
                .stream()
                .filter(m -> !m.isDeletedForEveryone())
                .filter(m -> !isHiddenFor(m, myEmail))
                .filter(m -> m.getSentAt().isAfter(lastReadAt))
                .collect(Collectors.toList());

        boolean hasNewFromOthers = newMessages.stream()
                .anyMatch(m -> !m.getSender().getEmail().equalsIgnoreCase(myEmail));

        if (memberOpt.isPresent()) {
            memberOpt.get().setLastReadAt(java.time.LocalDateTime.now());
            conversationMemberRepository.save(memberOpt.get());
        }

        if (newMessages.isEmpty() || !hasNewFromOthers) {
            result.put("hasNew", false);
            return result;
        }

        String transcript = newMessages.stream()
                .map(m -> m.getSender().getName() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));

        result.put("hasNew", true);
        result.put("count", newMessages.size());
        result.put("summary", callGeminiSummarize(transcript));
        return result;
    }

    private boolean isHiddenFor(Message m, String email) {
        if (m.getHiddenFor() == null || m.getHiddenFor().isBlank()) return false;
        return Arrays.asList(m.getHiddenFor().split(",")).contains(email);
    }

    // Builds a "Username: message" transcript for a conversation, for Gemini prompts
    private String buildTranscript(Long conversationId) {
        return messageRepository.findByConversationIdOrderBySentAtAsc(conversationId)
                .stream()
                .filter(m -> !m.isDeletedForEveryone())
                .map(m -> m.getSender().getName() + ": " + m.getContent())
                .collect(Collectors.joining("\n"));
    }

    // ---------- OLD raw-text routes (kept for backward compatibility) ----------

    @PostMapping("/summarize")
    @ResponseBody
    public String summarizeChat(@RequestBody String chatText) {
        return callGeminiSummarize(chatText);
    }

    @PostMapping("/extractActions")
    @ResponseBody
    public String extractActions(@RequestBody String chatText) {
        return callGeminiExtractActions(chatText);
    }

    // ---------- NEW conversation-scoped routes ----------

    @PostMapping("/summarize/{conversationId}")
    @ResponseBody
    public String summarizeConversation(@PathVariable Long conversationId) {
        String transcript = buildTranscript(conversationId);
        if (transcript.isBlank()) {
            return "No messages yet in this conversation.";
        }
        return callGeminiSummarize(transcript);
    }

    @PostMapping("/extractActions/{conversationId}")
    @ResponseBody
    public String extractActionsForConversation(@PathVariable Long conversationId) {
        String transcript = buildTranscript(conversationId);
        if (transcript.isBlank()) {
            return "[]";
        }
        return callGeminiExtractActions(transcript);
    }

    // ---------- Shared Gemini calls ----------

    private String callGeminiSummarize(String chatText) {
        try {
            String prompt = "You are a chat summarizer. Summarize this conversation clearly and "
                    + "concisely in 3-5 sentences. Mention who said what, key topics discussed, and "
                    + "any conclusions or decisions made.\n\nConversation:\n" + chatText;

            String requestBody = "{"
                    + "\"contents\": [{"
                    + "\"parts\": [{\"text\": \"" + escapeJson(prompt) + "\"}]"
                    + "}]"
                    + "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + API_KEY))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            if (response.statusCode() != 200) {
                return "Error generating summary: Gemini API returned " + response.statusCode() + " - " + body;
            }

            return extractGeminiText(body);
        } catch (Exception e) {
            return "Error generating summary: " + e.getMessage();
        }
    }

    private String callGeminiExtractActions(String chatText) {
        try {
            String prompt = "You are an assistant that extracts action items from a chat conversation. "
                    + "Read the conversation and return ONLY a JSON array (no markdown, no explanation) "
                    + "of objects with exactly these fields: \"task\", \"owner\", \"deadline\". "
                    + "If no owner or deadline is mentioned for a task, use \"Unassigned\" or \"Not specified\". "
                    + "If there are no action items, return an empty array []. "
                    + "Do not wrap the JSON in code fences.\n\nConversation:\n" + chatText;
            String requestBody = "{"
                    + "\"contents\": [{"
                    + "\"parts\": [{\"text\": \"" + escapeJson(prompt) + "\"}]"
                    + "}]"
                    + "}";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(GEMINI_URL + API_KEY))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() != 200) {
                return "[]";
            }
            String text = extractGeminiText(body).trim();
            if (text.startsWith("```")) {
                text = text.replaceAll("^```(json)?", "").replaceAll("```$", "").trim();
            }
            return text;
        } catch (Exception e) {
            return "[]";
        }
    }

    private String extractGeminiText(String body) {
        Pattern pattern = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            String raw = matcher.group(1);
            return raw.replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return "Error generating summary: could not parse Gemini response - " + body;
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}