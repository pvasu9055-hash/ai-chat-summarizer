package com.chat.chatsummarizer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PageController {

    @GetMapping("/")
    @ResponseBody
    public String home() {
        return "AI Chat Summarizer backend is running.";
    }
}
