package com.sigeu.api.repository;

import com.sigeu.api.model.Emergency;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface EmergencyRepository extends JpaRepository<Emergency, Long> {
    List<Emergency> findByTargetEntityOrderByCreatedAtDesc(String targetEntity);
    List<Emergency> findAllByOrderByCreatedAtDesc();
    List<Emergency> findByStatusOrderByCreatedAtAsc(String status);
    List<Emergency> findByStatusAndAutoResolveAtLessThanEqual(String status, LocalDateTime autoResolveAt);
    List<Emergency> findByStatusAndAutoDeleteAtLessThanEqual(String status, LocalDateTime autoDeleteAt);
    List<Emergency> findByTargetEntityAndStatus(String targetEntity, String status);
}
