package com.chat.chatsummarizer.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.ArrayList;

@Controller
public class ChatController {

    @MessageMapping("/sendMessage")
    @SendTo("/topic/messages")
    public Map<String, String> sendMessage(Map<String, String> message) {
        return message;
    }

    @GetMapping("/")
    public String home() {
        return "chat";
    }

    @PostMapping("/summarize")
    @ResponseBody
    public String summarizeChat(@RequestBody String chatText) {
        String[] lines = chatText.split("\n");
        Set<String> participants = new LinkedHashSet<>();
        List<String> messages = new ArrayList<>();

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(": ", 2);
            if (parts.length == 2) {
                participants.add(parts[0].trim());
                messages.add(parts[1].trim());
            }
        }

        String names = String.join(" and ", participants);
        String firstMsg = "";
        for (String msg : messages) {
            if (msg.length() > 10) {
                firstMsg = msg;
                break;
            }
        }

        String summary = names + " had a conversation with " + messages.size() + " messages. " +
            firstMsg + ". The conversation covered key discussion points between the participants.";

        return summary;
    }
}