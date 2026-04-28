package com.sigeu.api.controller;

import com.sigeu.api.model.Emergency;
import com.sigeu.api.repository.EmergencyRepository;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/emergencies")
@CrossOrigin(origins = "*")
public class EmergencyController {
    private final EmergencyRepository repository;
    public EmergencyController(EmergencyRepository repository) { this.repository = repository; }

    @GetMapping
    public List<Emergency> get(@RequestParam(required = false) String target) {
        if (target != null) return repository.findByTargetEntity(target);
        return repository.findAll();
    }

    @PostMapping
    public Emergency create(@RequestBody Emergency e) { return repository.save(e); }
}