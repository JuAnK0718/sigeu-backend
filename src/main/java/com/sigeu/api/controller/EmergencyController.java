package com.sigeu.api.controller;

import com.sigeu.api.model.Emergency;
import com.sigeu.api.repository.EmergencyRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/emergencies")
@CrossOrigin(origins = "*")
public class EmergencyController {
    private final EmergencyRepository repository;

    public EmergencyController(EmergencyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Emergency> get(@RequestParam(required = false) String target) {
        if (target != null) return repository.findByTargetEntity(target);
        return repository.findAll();
    }

    @PostMapping
    public Emergency create(@RequestBody Emergency e) {
        return repository.save(e);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return repository.findById(id).map(emergency -> {
            emergency.setStatus(body.get("status"));
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
}