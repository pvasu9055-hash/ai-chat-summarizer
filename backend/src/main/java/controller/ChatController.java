package com.chat.chatsummarizer.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class ChatController {

    @Value("${gemini.api.key}")
    private String API_KEY;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    @MessageMapping("/sendMessage")
    @SendTo("/topic/messages")
    public Map<String, String> sendMessage(Map<String, String> message) {
        return message;
    }

    @PostMapping("/summarize")
    @ResponseBody
    public String summarizeChat(@RequestBody String chatText) {
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

    // Extracts the "text" field from Gemini's response JSON without a full JSON library.
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
