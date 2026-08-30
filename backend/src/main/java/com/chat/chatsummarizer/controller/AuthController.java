package com.chat.chatsummarizer.controller;
import com.chat.chatsummarizer.model.User;
import com.chat.chatsummarizer.repository.UserRepository;
import com.chat.chatsummarizer.service.EmailService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
public class AuthController {
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public AuthController(UserRepository userRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.emailService = emailService;
    }

    private String generateOtp() {
        int code = 100000 + RANDOM.nextInt(900000);
        return String.valueOf(code);
    }

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody Map<String, String> body) {
        String name = body.getOrDefault("name", "").trim();
        String password = body.getOrDefault("password", "");
        String email = body.getOrDefault("email", "").trim().toLowerCase();

        if (name.isEmpty() || password.isEmpty() || email.isEmpty()) {
            return Map.of("success", false, "message", "Name, password, and email are required.");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            return Map.of("success", false, "message", "Invalid email format.");
        }
        if (userRepository.existsByEmail(email)) {
            return Map.of("success", false, "message", "Email already registered.");
        }

        User user = new User(name, encoder.encode(password), email);
        String otp = generateOtp();
        user.setOtpCode(otp);
        user.setEmailVerified(false);
        userRepository.save(user);

        emailService.sendOtpEmail(email, name, otp);

        return Map.of("success", true, "message", "Registered. Please check your email for the OTP code.");
    }

    @PostMapping("/verify-otp")
    public Map<String, Object> verifyOtp(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String otp = body.getOrDefault("otp", "").trim();

        return userRepository.findByEmail(email)
                .map(u -> {
                    if (u.isEmailVerified()) {
                        return Map.<String, Object>of("success", true, "message", "Email already verified.");
                    }
                    if (u.getOtpCode() != null && u.getOtpCode().equals(otp)) {
                        u.setEmailVerified(true);
                        u.setOtpCode(null);
                        userRepository.save(u);
                        return Map.<String, Object>of("success", true, "message", "Email verified successfully.");
                    }
                    return Map.<String, Object>of("success", false, "message", "Invalid OTP code.");
                })
                .orElse(Map.of("success", false, "message", "No account found with that email."));
    }

    @PostMapping("/resend-otp")
    public Map<String, Object> resendOtp(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        return userRepository.findByEmail(email)
                .map(u -> {
                    if (u.isEmailVerified()) {
                        return Map.<String, Object>of("success", false, "message", "Email already verified.");
                    }
                    String otp = generateOtp();
                    u.setOtpCode(otp);
                    userRepository.save(u);
                    emailService.sendOtpEmail(u.getEmail(), u.getName(), otp);
                    return Map.<String, Object>of("success", true, "message", "New OTP sent.");
                })
                .orElse(Map.of("success", false, "message", "No account found with that email."));
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").trim().toLowerCase();
        String password = body.getOrDefault("password", "");
        return userRepository.findByEmail(email)
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .map(u -> {
                    if (!u.isEmailVerified()) {
                        return Map.<String, Object>of("success", false, "message", "Please verify your email before logging in.", "needsVerification", true);
                    }
                    return Map.<String, Object>of("success", true, "name", u.getName());
                })
                .orElse(Map.of("success", false, "message", "Invalid email or password."));
    }
}
