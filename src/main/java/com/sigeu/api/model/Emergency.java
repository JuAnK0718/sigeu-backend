package com.sigeu.api.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "emergencies")
public class Emergency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String location;
    private String type;
    private String status;
    private String targetEntity;
    private String assignedOperatorUsername;
    private Integer assignedUnits;
    private String resourceLabel;
    private Integer estimatedResolveMinutes;
    private Boolean autoManaged;
    @Column(length = 500)
    private String operationalNote;

    // Evidencia visual enviada desde el frontend.
    @Column(columnDefinition = "TEXT")
    private String image;

    private LocalDateTime createdAt;
    private LocalDateTime autoStartedAt;
    private LocalDateTime autoResolveAt;
    private LocalDateTime autoDeleteAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        if (this.status == null || this.status.isBlank()) this.status = "PENDING";
        if (this.assignedUnits == null) this.assignedUnits = 0;
        if (this.autoManaged == null) this.autoManaged = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }

    public String getAssignedOperatorUsername() { return assignedOperatorUsername; }
    public void setAssignedOperatorUsername(String assignedOperatorUsername) { this.assignedOperatorUsername = assignedOperatorUsername; }

    public Integer getAssignedUnits() { return assignedUnits; }
    public void setAssignedUnits(Integer assignedUnits) { this.assignedUnits = assignedUnits; }

    public String getResourceLabel() { return resourceLabel; }
    public void setResourceLabel(String resourceLabel) { this.resourceLabel = resourceLabel; }

    public Integer getEstimatedResolveMinutes() { return estimatedResolveMinutes; }
    public void setEstimatedResolveMinutes(Integer estimatedResolveMinutes) { this.estimatedResolveMinutes = estimatedResolveMinutes; }

    public Boolean getAutoManaged() { return autoManaged; }
    public void setAutoManaged(Boolean autoManaged) { this.autoManaged = autoManaged; }

    public String getOperationalNote() { return operationalNote; }
    public void setOperationalNote(String operationalNote) { this.operationalNote = operationalNote; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getAutoStartedAt() { return autoStartedAt; }
    public void setAutoStartedAt(LocalDateTime autoStartedAt) { this.autoStartedAt = autoStartedAt; }

    public LocalDateTime getAutoResolveAt() { return autoResolveAt; }
    public void setAutoResolveAt(LocalDateTime autoResolveAt) { this.autoResolveAt = autoResolveAt; }

    public LocalDateTime getAutoDeleteAt() { return autoDeleteAt; }
    public void setAutoDeleteAt(LocalDateTime autoDeleteAt) { this.autoDeleteAt = autoDeleteAt; }

    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}
