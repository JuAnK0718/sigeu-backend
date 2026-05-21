package com.sigeu.api.service;

import com.sigeu.api.model.Emergency;
import com.sigeu.api.repository.EmergencyRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EmergencyService {
    private final EmergencyRepository repository;

    public EmergencyService(EmergencyRepository repository) {
        this.repository = repository;
    }

    public List<Emergency> getAllEmergencies() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    public List<Emergency> getEmergenciesByEntity(String entity) {
        return repository.findByTargetEntityOrderByCreatedAtDesc(entity);
    }

    public Emergency createEmergency(Emergency emergency) {
        return repository.save(emergency);
    }
}
