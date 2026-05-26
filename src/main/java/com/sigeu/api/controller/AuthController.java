package com.sigeu.api.controller;

import com.sigeu.api.dto.AuthResponse;
import com.sigeu.api.model.ResourceInventory;
import com.sigeu.api.model.User;
import com.sigeu.api.repository.ResourceInventoryRepository;
import com.sigeu.api.repository.UserRepository;
import com.sigeu.api.security.JwtService;
import com.sigeu.api.security.PasswordService;
import com.sigeu.api.validation.InputRules;
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
    private final ResourceInventoryRepository inventoryRepository;
    private final JwtService jwtService;
    private final PasswordService passwordService;

    public AuthController(UserRepository repository, ResourceInventoryRepository inventoryRepository, JwtService jwtService, PasswordService passwordService) {
        this.repository = repository;
        this.inventoryRepository = inventoryRepository;
        this.jwtService = jwtService;
        this.passwordService = passwordService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> credentials) {
        String username = normalizeUsername(credentials.get("username"));
        String password = credentials.get("password");

        if (username == null || password == null || password.isBlank()) {
            return ResponseEntity.badRequest().body("Usuario y password son obligatorios");
        }
        if (!InputRules.validUsername(username) || InputRules.exceeds(password, InputRules.PASSWORD_MAX)) {
            return ResponseEntity.badRequest().body("Datos de acceso no validos");
        }

        Optional<User> user = repository.findByUsername(username);

        if (user.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Nombre de usuario no encontrado");
        }

        User authenticatedUser = user.get();
        if (!passwordService.matches(password, authenticatedUser.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Password incorrecto");
        }

        if (passwordService.needsHashUpgrade(authenticatedUser.getPassword())) {
            authenticatedUser.setPassword(passwordService.hash(password));
            repository.save(authenticatedUser);
        }

        return ResponseEntity.ok(new AuthResponse(authenticatedUser, jwtService.generateToken(authenticatedUser)));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User newUser) {
        try {
            String username = normalizeUsername(newUser.getUsername());
            String password = newUser.getPassword();
            String role = normalizeRole(newUser.getRole());
            String name = InputRules.clean(newUser.getName());

            if (username == null || password == null || password.isBlank() || role == null) {
                return ResponseEntity.badRequest().body("Usuario, password y rol son obligatorios");
            }
            if (!InputRules.validUsername(username)) {
                return ResponseEntity.badRequest().body("Usuario no valido. Usa 4-30 caracteres: minusculas, numeros o guion bajo");
            }
            if (!InputRules.validPassword(password)) {
                return ResponseEntity.badRequest().body("Password no valido. Usa 8-72 caracteres, al menos una mayuscula y solo @ # _ . -");
            }
            if (InputRules.exceeds(name, InputRules.NAME_MAX)) {
                return ResponseEntity.badRequest().body("Nombre demasiado largo");
            }
            if (!VALID_ROLES.contains(role)) {
                return ResponseEntity.badRequest().body("Rol no permitido");
            }
            if (repository.findByUsername(username).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Este nombre de usuario ya esta en uso");
            }

            newUser.setUsername(username);
            newUser.setPassword(passwordService.hash(password));
            newUser.setRole(role);
            newUser.setName(name);
            repository.save(newUser);
            createEmptyInventoryForNewEntity(username, role);
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

    private void createEmptyInventoryForNewEntity(String username, String role) {
        if ("CITIZEN".equals(role) || inventoryRepository.findByUsername(username).isPresent()) {
            return;
        }

        ResourceInventory inventory = new ResourceInventory();
        inventory.setUsername(username);
        inventory.setTargetEntity(role);
        inventory.setTotalUnits(0);
        inventory.setDailyAddedUnits(0);
        inventory.setDefaultResourcesApplied(true);
        inventoryRepository.save(inventory);
    }
}
