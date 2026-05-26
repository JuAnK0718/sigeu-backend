package com.sigeu.api.controller;

import com.sigeu.api.dto.ResourceSummary;
import com.sigeu.api.model.Emergency;
import com.sigeu.api.repository.EmergencyRepository;
import com.sigeu.api.service.EmergencyWorkflowService;
import com.sigeu.api.validation.InputRules;
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
    private static final Set<String> VALID_STATUSES = Set.of("PENDING", "WAITING", "IN_PROGRESS", "RESOLVED");
    private final EmergencyRepository repository;
    private final EmergencyWorkflowService workflowService;

    public EmergencyController(EmergencyRepository repository, EmergencyWorkflowService workflowService) {
        this.repository = repository;
        this.workflowService = workflowService;
    }

    @GetMapping
    public List<Emergency> get(@RequestParam(required = false) String target) {
        workflowService.runAutomationCycle();
        String normalizedTarget = normalize(target);
        if (normalizedTarget != null) return repository.findByTargetEntityOrderByCreatedAtDesc(normalizedTarget);
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/resources")
    public ResponseEntity<?> resources(@RequestParam String target) {
        String normalizedTarget = normalize(target);
        if (normalizedTarget == null || !VALID_TARGETS.contains(normalizedTarget)) {
            return ResponseEntity.badRequest().body("Entidad destino no valida");
        }
        ResourceSummary summary = workflowService.resourcesFor(normalizedTarget);
        return summary == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(summary);
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody Emergency emergency) {
        String title = InputRules.clean(emergency.getTitle());
        String description = InputRules.clean(emergency.getDescription());
        String location = InputRules.clean(emergency.getLocation());
        String type = InputRules.clean(emergency.getType());

        if (InputRules.isBlank(title) || InputRules.isBlank(description) || InputRules.isBlank(location)) {
            return ResponseEntity.badRequest().body("Titulo, descripcion y ubicacion son obligatorios");
        }
        if (InputRules.exceeds(title, InputRules.TITLE_MAX)) {
            return ResponseEntity.badRequest().body("Titulo demasiado largo");
        }
        if (InputRules.exceeds(description, InputRules.DESCRIPTION_MAX)) {
            return ResponseEntity.badRequest().body("Descripcion demasiado larga");
        }
        if (InputRules.exceeds(location, InputRules.LOCATION_MAX)) {
            return ResponseEntity.badRequest().body("Ubicacion demasiado larga");
        }
        if (InputRules.exceeds(type, InputRules.TYPE_MAX)) {
            return ResponseEntity.badRequest().body("Tipo demasiado largo");
        }
        if (InputRules.exceeds(emergency.getImage(), InputRules.IMAGE_MAX)) {
            return ResponseEntity.badRequest().body("Imagen demasiado grande");
        }

        String target = normalize(emergency.getTargetEntity());
        if (target == null || !VALID_TARGETS.contains(target)) {
            return ResponseEntity.badRequest().body("Entidad destino no valida");
        }

        emergency.setTitle(title);
        emergency.setDescription(description);
        emergency.setLocation(location);
        emergency.setType(type);
        emergency.setTargetEntity(target);
        if (!InputRules.isBlank(emergency.getStatus())) {
            String status = normalize(emergency.getStatus());
            if (!VALID_STATUSES.contains(status)) {
                return ResponseEntity.badRequest().body("Estado no valido");
            }
            emergency.setStatus(status);
        }

        return ResponseEntity.ok(workflowService.saveAndPlan(emergency));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String status = normalize(body.get("status"));
        if (status == null || !VALID_STATUSES.contains(status)) {
            return ResponseEntity.badRequest().body("Estado no valido");
        }

        return workflowService.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
}
