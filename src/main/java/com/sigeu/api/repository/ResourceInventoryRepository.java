package com.sigeu.api.repository;

import com.sigeu.api.model.ResourceInventory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResourceInventoryRepository extends JpaRepository<ResourceInventory, Long> {
    Optional<ResourceInventory> findByUsername(String username);
}
