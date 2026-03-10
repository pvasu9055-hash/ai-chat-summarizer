package com.chat.chatsummarizer.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ChatController {

    @MessageMapping("/sendMessage")
    @SendTo("/topic/messages")
    public String sendMessage(String message) {
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
        StringBuilder summary = new StringBuilder("Summary:\n");
        for (String line : lines) {
            if (!line.trim().isEmpty()) {
                summary.append("- ").append(line.trim()).append("\n");
            }
        }
        return summary.toString();
    }
}