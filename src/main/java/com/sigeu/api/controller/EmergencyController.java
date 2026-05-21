package com.sigeu.api.controller;

import com.sigeu.api.model.Emergency;
import com.sigeu.api.repository.EmergencyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/emergencies")
@CrossOrigin(origins = "*")
public class EmergencyController {
    private static final Set<String> VALID_TARGETS = Set.of("POLICIA", "BOMBEROS", "HOSPITAL");
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "IN_PROGRESS", "RESOLVED");
    private final EmergencyRepository repository;

    public EmergencyController(EmergencyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Emergency> get(@RequestParam(required = false) String target) {
        String normalizedTarget = normalize(target);
        if (normalizedTarget != null) return repository.findByTargetEntityOrderByCreatedAtDesc(normalizedTarget);
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Emergency emergency) {
        if (isBlank(emergency.getTitle()) || isBlank(emergency.getDescription()) || isBlank(emergency.getLocation())) {
            return ResponseEntity.badRequest().body("Titulo, descripcion y ubicacion son obligatorios");
        }

        String target = normalize(emergency.getTargetEntity());
        if (target == null || !VALID_TARGETS.contains(target)) {
            return ResponseEntity.badRequest().body("Entidad destino no valida");
        }

        emergency.setTargetEntity(target);
        if (!isBlank(emergency.getStatus())) {
            String status = normalize(emergency.getStatus());
            if (!VALID_STATUSES.contains(status)) {
                return ResponseEntity.badRequest().body("Estado no valido");
            }
            emergency.setStatus(status);
        }

        return ResponseEntity.ok(repository.save(emergency));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = normalize(body.get("status"));
        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.badRequest().body("Estado no valido");
        }

        return repository.findById(id).map(emergency -> {
            emergency.setStatus(status);
            return ResponseEntity.ok(repository.save(emergency));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        return repository.findById(id).map(emergency -> {
            repository.delete(emergency);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
