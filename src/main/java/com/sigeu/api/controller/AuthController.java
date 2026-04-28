package com.sigeu.api.controller;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final UserRepository repository;
    public AuthController(UserRepository repository) { this.repository = repository; }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        Optional<User> user = repository.findByUsername(credentials.get("username"));
        if (user.isPresent() && user.get().getPassword().equals(credentials.get("password"))) {
            return ResponseEntity.ok(user.get());
        }
        return ResponseEntity.status(401).body("Error");
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        return ResponseEntity.ok(repository.save(user));
    }
}