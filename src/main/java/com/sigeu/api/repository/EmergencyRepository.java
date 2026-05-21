package com.sigeu.api.repository;

import com.sigeu.api.model.Emergency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmergencyRepository extends JpaRepository<Emergency, Long> {
    List<Emergency> findByTargetEntityOrderByCreatedAtDesc(String targetEntity);
    List<Emergency> findAllByOrderByCreatedAtDesc();
}
