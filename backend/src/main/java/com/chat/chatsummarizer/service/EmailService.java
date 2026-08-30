package com.chat.chatsummarizer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    @Value("${RESEND_API_KEY:}")
    private String resendApiKey;

    private final HttpClient client = HttpClient.newHttpClient();

    public void sendOtpEmail(String toEmail, String name, String otp) {
        String jsonBody = String.format(
            "{\"from\":\"hello@vasutech.online\",\"to\":[\"%s\"],\"subject\":\"Your AI Chat Summarizer verification code\",\"html\":\"<p>Hi %s,</p><p>Your verification code is:</p><h2>%s</h2><p>This code expires in 10 minutes.</p>\"}",
            toEmail, name, otp
        );

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.resend.com/emails"))
            .header("Authorization", "Bearer " + resendApiKey)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
            .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Resend response: " + response.statusCode() + " " + response.body());
        } catch (Exception e) {
            System.err.println("Failed to send OTP email: " + e.getMessage());
        }
    }
}
