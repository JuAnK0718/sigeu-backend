package com.sigeu.api.dto;

public class ResourceSummary {
    private String targetEntity;
    private String label;
    private String unitName;
    private int totalUnits;
    private int usedUnits;
    private int availableUnits;
    private int dailyAddedUnits;
    private int dailyAddLimit;
    private int remainingDailyAdd;
    private int waitingIncidents;
    private int inProgressIncidents;

    public ResourceSummary(String targetEntity, String label, String unitName, int totalUnits, int usedUnits, int dailyAddedUnits, int dailyAddLimit, int waitingIncidents, int inProgressIncidents) {
        this.targetEntity = targetEntity;
        this.label = label;
        this.unitName = unitName;
        this.totalUnits = totalUnits;
        this.usedUnits = usedUnits;
        this.availableUnits = Math.max(totalUnits - usedUnits, 0);
        this.dailyAddedUnits = dailyAddedUnits;
        this.dailyAddLimit = dailyAddLimit;
        this.remainingDailyAdd = Math.max(dailyAddLimit - dailyAddedUnits, 0);
        this.waitingIncidents = waitingIncidents;
        this.inProgressIncidents = inProgressIncidents;
    }

    public String getTargetEntity() { return targetEntity; }
    public String getLabel() { return label; }
    public String getUnitName() { return unitName; }
    public int getTotalUnits() { return totalUnits; }
    public int getUsedUnits() { return usedUnits; }
    public int getAvailableUnits() { return availableUnits; }
    public int getDailyAddedUnits() { return dailyAddedUnits; }
    public int getDailyAddLimit() { return dailyAddLimit; }
    public int getRemainingDailyAdd() { return remainingDailyAdd; }
    public int getWaitingIncidents() { return waitingIncidents; }
    public int getInProgressIncidents() { return inProgressIncidents; }
}
