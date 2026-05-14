package com.sigeu.api.controller;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {
    private final UserRepository repository;

    public AuthController(UserRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        Optional<User> user = repository.findByUsername(credentials.get("username"));

        if (!user.isPresent()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nombre de usuario no encontrado");
        }

        if (!user.get().getPassword().equals(credentials.get("password"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Contraseña incorrecta");
        }

        return ResponseEntity.ok(user.get());
    }

    // --- MÉTODOS NUEVOS (AHORA SÍ DENTRO DE LA CLASE) ---

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User newUser) {
        try {
            // 1. Verificamos de verdad si el usuario ya existe en tu BD
            Optional<User> existingUser = repository.findByUsername(newUser.getUsername());
            if (existingUser.isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Este nombre de usuario ya está en uso");
            }

            // 2. Si no existe, lo guardamos en la base de datos real
            repository.save(newUser);
            return ResponseEntity.ok().body("Usuario creado exitosamente");

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error interno al crear el usuario.");
        }
    }

    @PostMapping("/recover")
    public ResponseEntity<?> recoverPassword(@RequestBody Map<String, String> request) {
        String username = request.get("username");

        // 1. Buscamos de verdad en tu BD si el usuario existe
        Optional<User> user = repository.findByUsername(username);

        if (user.isPresent()) {
            // Si el usuario existe, se manda el OK. (En un sistema futuro aquí iría el envío de email)
            return ResponseEntity.ok().body("Instrucciones de recuperación enviadas");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado en el sistema");
        }
    }

}