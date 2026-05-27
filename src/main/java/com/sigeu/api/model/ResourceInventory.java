package com.sigeu.api.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "resource_inventories", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username")
})
public class ResourceInventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String targetEntity;

    private Integer totalUnits;
    private Integer dailyAddedUnits;
    private Integer dailyRemovedUnits;
    private LocalDate dailyLimitDate;
    private Boolean defaultResourcesApplied;

    @PrePersist
    protected void onCreate() {
        if (this.totalUnits == null) this.totalUnits = 0;
        if (this.dailyAddedUnits == null) this.dailyAddedUnits = 0;
        if (this.dailyRemovedUnits == null) this.dailyRemovedUnits = 0;
        if (this.dailyLimitDate == null) this.dailyLimitDate = LocalDate.now();
        if (this.defaultResourcesApplied == null) this.defaultResourcesApplied = false;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }

    public Integer getTotalUnits() { return totalUnits; }
    public void setTotalUnits(Integer totalUnits) { this.totalUnits = totalUnits; }

    public Integer getDailyAddedUnits() { return dailyAddedUnits; }
    public void setDailyAddedUnits(Integer dailyAddedUnits) { this.dailyAddedUnits = dailyAddedUnits; }

    public Integer getDailyRemovedUnits() { return dailyRemovedUnits; }
    public void setDailyRemovedUnits(Integer dailyRemovedUnits) { this.dailyRemovedUnits = dailyRemovedUnits; }

    public LocalDate getDailyLimitDate() { return dailyLimitDate; }
    public void setDailyLimitDate(LocalDate dailyLimitDate) { this.dailyLimitDate = dailyLimitDate; }

    public Boolean getDefaultResourcesApplied() { return defaultResourcesApplied; }
    public void setDefaultResourcesApplied(Boolean defaultResourcesApplied) { this.defaultResourcesApplied = defaultResourcesApplied; }
}
