package com.sigeu.api.repository;

import com.sigeu.api.model.Emergency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EmergencyRepository extends JpaRepository<Emergency, Long> {
    List<Emergency> findByTargetEntityOrderByCreatedAtDesc(String targetEntity);
    List<Emergency> findAllByOrderByCreatedAtDesc();
    List<Emergency> findByStatusOrderByCreatedAtAsc(String status);
    List<Emergency> findByStatusAndAutoResolveAtLessThanEqual(String status, LocalDateTime autoResolveAt);
    List<Emergency> findByStatusAndAutoDeleteAtLessThanEqual(String status, LocalDateTime autoDeleteAt);
    List<Emergency> findByTargetEntityAndStatus(String targetEntity, String status);
    List<Emergency> findByTargetEntityAndStatusAndAssignedOperatorUsername(String targetEntity, String status, String assignedOperatorUsername);

    @Query("""
            select e from Emergency e
            where e.targetEntity = :targetEntity
              and (e.assignedOperatorUsername is null or e.assignedOperatorUsername = :username)
            order by e.createdAt desc
            """)
    List<Emergency> findVisibleForOperator(@Param("targetEntity") String targetEntity, @Param("username") String username);

    @Query("""
            select e from Emergency e
            where e.targetEntity = :targetEntity
              and e.status in :statuses
              and e.assignedOperatorUsername is null
            order by e.createdAt asc
            """)
    List<Emergency> findUnassignedCandidates(@Param("targetEntity") String targetEntity, @Param("statuses") Collection<String> statuses);
}
