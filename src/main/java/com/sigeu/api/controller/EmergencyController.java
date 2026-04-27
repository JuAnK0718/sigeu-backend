package com.sigeu.api.controller;

import com.sigeu.api.model.Emergency;
import com.sigeu.api.service.EmergencyService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/emergencies")
@CrossOrigin(origins = "*")
public class EmergencyController {
    private final EmergencyService service;

    public EmergencyController(EmergencyService service) {
        this.service = service;
    }

    @GetMapping
    public List<Emergency> getEmergencies(@RequestParam(required = false) String target) {
        if (target != null) {
            return service.getEmergenciesByEntity(target);
        }
        return service.getAllEmergencies();
    }

    @PostMapping
    public Emergency createEmergency(@RequestBody Emergency emergency) {
        return service.createEmergency(emergency);
    }
}