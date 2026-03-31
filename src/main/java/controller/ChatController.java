package com.chat.chatsummarizer.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

@Controller
public class ChatController {

    @Value("${groq.api.key}")
    private String API_KEY;

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
        try {
            String prompt = "You are a chat summarizer. Summarize this conversation clearly and concisely in 3-5 sentences. Mention who said what, key topics discussed, and any conclusions or decisions made.\n\nConversation:\n" + chatText;

            // ✅ Gemini request format
            String requestBody = "{"
                    + "\"contents\": [{"
                    + "\"parts\": [{\"text\": \"" + escapeJson(prompt) + "\"}]"
                    + "}]"
                    + "}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.groq.com/openai/v1/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + API_KEY)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"model\":\"llama-3.3-70b-versatile\",\"messages\":[{\"role\":\"user\",\"content\":\"" + escapeJson(prompt) + "\"}]}"))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            // Extract just the summary text
            int start = body.indexOf("\"content\":\"") + 11;
            int end = body.indexOf("\"},\"logprobs\"", start);
            return body.substring(start, end).replace("\\n", "\n");

        } catch (Exception e) {
            return "Error generating summary: " + e.getMessage();
        }
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