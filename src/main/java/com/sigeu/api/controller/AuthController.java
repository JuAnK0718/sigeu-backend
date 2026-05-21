package com.sigeu.api.controller;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private static final Set<String> VALID_ROLES = Set.of("CITIZEN", "POLICIA", "BOMBEROS", "HOSPITAL");
    private final UserRepository repository;

    public AuthController(UserRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = normalizeUsername(credentials.get("username"));
        String password = credentials.get("password");

        if (username == null || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("Usuario y password son obligatorios");
        }

        Optional<User> user = repository.findByUsername(username);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nombre de usuario no encontrado");
        }

        if (!user.get().getPassword().equals(password)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password incorrecto");
        }

        return ResponseEntity.ok(user.get());
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User newUser) {
        try {
            String username = normalizeUsername(newUser.getUsername());
            String password = newUser.getPassword();
            String role = normalizeRole(newUser.getRole());

            if (username == null || password == null || password.isBlank() || role == null) {
                return ResponseEntity.badRequest().body("Usuario, password y rol son obligatorios");
            }
            if (!VALID_ROLES.contains(role)) {
                return ResponseEntity.badRequest().body("Rol no permitido");
            }
            if (repository.findByUsername(username).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Este nombre de usuario ya esta en uso");
            }

            newUser.setUsername(username);
            newUser.setRole(role);
            repository.save(newUser);
            return ResponseEntity.ok().body("Usuario creado exitosamente");

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error interno al crear el usuario.");
        }
    }

    @PostMapping("/recover")
    public ResponseEntity<?> recoverPassword(@RequestBody Map<String, String> request) {
        String username = normalizeUsername(request.get("username"));

        if (username == null) {
            return ResponseEntity.badRequest().body("Usuario obligatorio");
        }

        Optional<User> user = repository.findByUsername(username);

        if (user.isPresent()) {
            return ResponseEntity.ok().body("Instrucciones de recuperacion enviadas");
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado en el sistema");
    }

    private String normalizeUsername(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase();
    }

    private String normalizeRole(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase();
    }
}
