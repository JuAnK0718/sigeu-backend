package com.sigeu.api.service;

import com.sigeu.api.dto.ResourceSummary;
import com.sigeu.api.model.Emergency;
import com.sigeu.api.model.ResourceInventory;
import com.sigeu.api.repository.EmergencyRepository;
import com.sigeu.api.repository.ResourceInventoryRepository;
import com.sigeu.api.security.JwtService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class EmergencyWorkflowService {
    private static final String PENDING = "PENDING";
    private static final String WAITING = "WAITING";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String RESOLVED = "RESOLVED";
    private static final int MEDICAL_SINGLE_MINUTES = 30;
    private static final int ROAD_MINOR_MINUTES = 30;
    private static final int ROAD_INJURY_MINUTES = 60;
    private static final int ROAD_MAJOR_MINUTES = 90;
    private static final int ROAD_BLOCKAGE_MINUTES = 180;
    private static final int ROAD_LANDSLIDE_MINUTES = 360;
    private static final int FIRE_MINUTES = 90;
    private static final int COMPLEX_FIRE_MINUTES = 150;
    private static final int POLICE_STANDARD_MINUTES = 45;
    private static final int POLICE_HIGH_RISK_MINUTES = 90;
    private static final int FALLBACK_HIGH_MINUTES = 45;

    private final EmergencyRepository repository;
    private final ResourceInventoryRepository inventoryRepository;
    private final Clock clock;
    private final boolean automationEnabled;
    private final int defaultResolveMinutes;
    private final int highPriorityResolveMinutes;
    private final int deleteAfterResolveMinutes;
    private final int dailyAddLimit;
    private final int dailyRemoveLimit;
    private final Map<String, ResourcePool> pools;

    public EmergencyWorkflowService(
            EmergencyRepository repository,
            ResourceInventoryRepository inventoryRepository,
            Clock clock,
            @Value("${sigeu.workflow.enabled:true}") boolean automationEnabled,
            @Value("${sigeu.workflow.resolve-minutes:15}") int defaultResolveMinutes,
            @Value("${sigeu.workflow.high-priority-resolve-minutes:10}") int highPriorityResolveMinutes,
            @Value("${sigeu.workflow.delete-after-resolve-minutes:10}") int deleteAfterResolveMinutes,
            @Value("${sigeu.resources.daily-add-limit:10}") int dailyAddLimit,
            @Value("${sigeu.resources.daily-remove-limit:10}") int dailyRemoveLimit,
            @Value("${sigeu.resources.policia:30}") int policeDefaultUnits,
            @Value("${sigeu.resources.hospital:15}") int ambulanceDefaultUnits,
            @Value("${sigeu.resources.bomberos:8}") int fireTruckDefaultUnits
    ) {
        this.repository = repository;
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
        this.automationEnabled = automationEnabled;
        this.defaultResolveMinutes = defaultResolveMinutes;
        this.highPriorityResolveMinutes = highPriorityResolveMinutes;
        this.deleteAfterResolveMinutes = deleteAfterResolveMinutes;
        this.dailyAddLimit = dailyAddLimit;
        this.dailyRemoveLimit = dailyRemoveLimit;
        this.pools = Map.of(
                "POLICIA", new ResourcePool("POLICIA", "Policia", "policias", policeDefaultUnits),
                "HOSPITAL", new ResourcePool("HOSPITAL", "Hospital", "ambulancias", ambulanceDefaultUnits),
                "BOMBEROS", new ResourcePool("BOMBEROS", "Bomberos", "camiones", fireTruckDefaultUnits)
        );
    }

    public Emergency prepareNewEmergency(Emergency emergency) {
        ResourceDecision decision = decideResources(emergency);
        emergency.setAssignedUnits(decision.units());
        emergency.setResourceLabel(decision.resourceLabel());
        emergency.setEstimatedResolveMinutes(decision.resolveMinutes());
        emergency.setAutoManaged(true);
        emergency.setOperationalNote("IA operativa recomienda " + decision.units() + " " + decision.resourceLabel() + ".");
        return emergency;
    }

    @Transactional
    public synchronized Emergency saveAndPlan(Emergency emergency) {
        Emergency saved = repository.save(prepareNewEmergency(emergency));
        finishExpiredCases();
        return repository.findById(saved.getId()).orElse(saved);
    }

    @Transactional
    public synchronized Optional<Emergency> updateStatus(Long id, String status, JwtService.AuthenticatedUser actor) {
        finishExpiredCases();
        return repository.findById(id).map(emergency -> {
            LocalDateTime now = now();

            if (IN_PROGRESS.equals(status)) {
                if (!isEntityActorFor(emergency.getTargetEntity(), actor)) {
                    return emergency;
                }
                ensureDecision(emergency);
                if (!canAssign(emergency, actor.username())) {
                    emergency.setStatus(WAITING);
                    emergency.setOperationalNote("Sin " + emergency.getResourceLabel() + " disponibles. Caso en espera automatica.");
                    return repository.save(emergency);
                }
                assign(emergency, now, actor.username());
                return repository.save(emergency);
            }

            if (RESOLVED.equals(status)) {
                if (!canManageEmergency(emergency, actor)) {
                    return emergency;
                }
                resolve(emergency, now);
                return repository.save(emergency);
            }

            emergency.setStatus(status);
            if (PENDING.equals(status) || WAITING.equals(status)) {
                emergency.setAutoStartedAt(null);
                emergency.setAutoResolveAt(null);
                emergency.setAutoDeleteAt(null);
                emergency.setResolvedAt(null);
            }
            return repository.save(emergency);
        });
    }

    @Transactional
    public synchronized ResourceSummary resourcesFor(String targetEntity, JwtService.AuthenticatedUser actor) {
        runAutomationCycleFor(targetEntity, actor);
        ResourcePool pool = pools.get(targetEntity);
        if (pool == null) return null;

        ResourceInventory inventory = isEntityActorFor(targetEntity, actor) ? inventoryFor(actor) : emptyInventory(targetEntity);
        resetDailyLimitIfNeeded(inventory);
        int used = isEntityActorFor(targetEntity, actor) ? usedUnits(targetEntity, actor.username()) : 0;
        int waiting = repository.findByTargetEntityAndStatus(targetEntity, WAITING).size();
        int inProgress = isEntityActorFor(targetEntity, actor)
                ? repository.findByTargetEntityAndStatusAndAssignedOperatorUsername(targetEntity, IN_PROGRESS, actor.username()).size()
                : repository.findByTargetEntityAndStatus(targetEntity, IN_PROGRESS).size();
        return summaryFor(pool, inventory, used, waiting, inProgress);
    }

    @Transactional
    public synchronized ResourceSummary addResources(String targetEntity, JwtService.AuthenticatedUser actor, int units) {
        if (!isEntityActorFor(targetEntity, actor)) {
            throw new IllegalArgumentException("Token de entidad requerido");
        }
        if (units < 1) {
            throw new IllegalArgumentException("Agrega al menos 1 recurso");
        }

        ResourceInventory inventory = inventoryFor(actor);
        resetDailyLimitIfNeeded(inventory);
        if (inventory.getDailyAddedUnits() + units > dailyAddLimit) {
            throw new IllegalArgumentException("Limite diario superado. Disponible hoy: " + Math.max(dailyAddLimit - inventory.getDailyAddedUnits(), 0));
        }

        inventory.setTotalUnits(safeInt(inventory.getTotalUnits()) + units);
        inventory.setDailyAddedUnits(safeInt(inventory.getDailyAddedUnits()) + units);
        inventoryRepository.save(inventory);
        runAutomationCycleFor(targetEntity, actor);
        return resourcesFor(targetEntity, actor);
    }

    @Transactional
    public synchronized ResourceSummary removeResources(String targetEntity, JwtService.AuthenticatedUser actor, int units) {
        if (!isEntityActorFor(targetEntity, actor)) {
            throw new IllegalArgumentException("Token de entidad requerido");
        }
        if (units < 1) {
            throw new IllegalArgumentException("Retira al menos 1 recurso");
        }

        ResourceInventory inventory = inventoryFor(actor);
        resetDailyLimitIfNeeded(inventory);

        int used = usedUnits(targetEntity, actor.username());
        int available = Math.max(safeInt(inventory.getTotalUnits()) - used, 0);
        if (units > available) {
            throw new IllegalArgumentException("No puedes retirar recursos ocupados. Disponibles para retirar: " + available);
        }
        if (inventory.getDailyRemovedUnits() + units > dailyRemoveLimit) {
            throw new IllegalArgumentException("Limite diario de retiro superado. Disponible hoy: " + Math.max(dailyRemoveLimit - inventory.getDailyRemovedUnits(), 0));
        }

        inventory.setTotalUnits(safeInt(inventory.getTotalUnits()) - units);
        inventory.setDailyRemovedUnits(safeInt(inventory.getDailyRemovedUnits()) + units);
        inventoryRepository.save(inventory);
        runAutomationCycleFor(targetEntity, actor);
        return resourcesFor(targetEntity, actor);
    }

    @Scheduled(fixedDelayString = "${sigeu.workflow.tick-ms:60000}")
    @Transactional
    public synchronized void runAutomationCycle() {
        if (!automationEnabled) return;
        finishExpiredCases();
    }

    @Transactional
    public synchronized void runAutomationCycleFor(String targetEntity, JwtService.AuthenticatedUser actor) {
        if (!automationEnabled) return;

        finishExpiredCases();
        if (!isEntityActorFor(targetEntity, actor)) return;
        inventoryFor(actor);

        List<Emergency> candidates = repository.findUnassignedCandidates(targetEntity, List.of(WAITING, PENDING));
        candidates.sort(Comparator.comparing(Emergency::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        for (Emergency emergency : candidates) {
            ensureDecision(emergency);
            if (canAssign(emergency, actor.username())) {
                assign(emergency, now(), actor.username());
            } else {
                emergency.setStatus(WAITING);
                emergency.setOperationalNote("Sin " + emergency.getResourceLabel() + " disponibles. Caso en espera automatica.");
            }
            repository.save(emergency);
        }
    }

    private void finishExpiredCases() {
        LocalDateTime now = now();

        List<Emergency> toResolve = repository.findByStatusAndAutoResolveAtLessThanEqual(IN_PROGRESS, now);
        for (Emergency emergency : toResolve) {
            resolve(emergency, now);
            repository.save(emergency);
        }

        List<Emergency> toDelete = repository.findByStatusAndAutoDeleteAtLessThanEqual(RESOLVED, now);
        if (!toDelete.isEmpty()) {
            repository.deleteAll(toDelete);
        }
    }

    private void assign(Emergency emergency, LocalDateTime now, String username) {
        emergency.setStatus(IN_PROGRESS);
        emergency.setAssignedOperatorUsername(username);
        emergency.setAutoStartedAt(now);
        emergency.setAutoResolveAt(now.plusMinutes(resolveMinutes(emergency)));
        emergency.setAutoDeleteAt(null);
        emergency.setResolvedAt(null);
        emergency.setOperationalNote("Despacho automatico: " + emergency.getAssignedUnits() + " " + emergency.getResourceLabel()
                + ". Resolucion estimada en " + formatDuration(resolveMinutes(emergency)) + ".");
    }

    private void resolve(Emergency emergency, LocalDateTime now) {
        emergency.setStatus(RESOLVED);
        emergency.setResolvedAt(now);
        emergency.setAutoDeleteAt(now.plusMinutes(deleteAfterResolveMinutes));
        emergency.setOperationalNote("Caso resuelto. Recursos liberados; limpieza automatica en " + deleteAfterResolveMinutes + " min.");
    }

    private void ensureDecision(Emergency emergency) {
        if (emergency.getAssignedUnits() != null && emergency.getAssignedUnits() > 0 && emergency.getResourceLabel() != null) {
            return;
        }
        ResourceDecision decision = decideResources(emergency);
        emergency.setAssignedUnits(decision.units());
        emergency.setResourceLabel(decision.resourceLabel());
        emergency.setEstimatedResolveMinutes(decision.resolveMinutes());
    }

    private boolean canAssign(Emergency emergency, String username) {
        ResourcePool pool = pools.get(emergency.getTargetEntity());
        if (pool == null) return false;
        ResourceInventory inventory = inventoryRepository.findByUsername(username).orElse(null);
        if (inventory == null) return false;
        return usedUnits(emergency.getTargetEntity(), username) + safeUnits(emergency) <= safeInt(inventory.getTotalUnits());
    }

    private int usedUnits(String targetEntity, String username) {
        return repository.findByTargetEntityAndStatusAndAssignedOperatorUsername(targetEntity, IN_PROGRESS, username).stream()
                .mapToInt(this::safeUnits)
                .sum();
    }

    private ResourceDecision decideResources(Emergency emergency) {
        String target = emergency.getTargetEntity();
        ResourcePool pool = pools.getOrDefault(target, pools.get("POLICIA"));
        String text = (emergency.getTitle() + " " + emergency.getDescription() + " " + emergency.getType()).toLowerCase(Locale.ROOT);
        boolean high = containsAny(text, "incendio", "fuego", "llamas", "explosion", "grave", "herido", "arma", "disparo", "violencia", "urgente", "inmediato", "inconsciente", "sin respuesta", "no responde");

        int units = switch (target) {
            case "BOMBEROS" -> fireTruckUnits(text);
            case "HOSPITAL" -> ambulanceUnits(text);
            default -> policeUnits(text);
        };

        units = Math.max(1, units);
        int minutes = estimateResolveMinutes(target, text, high);
        return new ResourceDecision(units, pool.unitName(), minutes);
    }

    private int policeUnits(String text) {
        int units = 2;
        if (containsAny(text, "robo", "asalto", "violencia")) units += 3;
        if (containsAny(text, "arma", "disparo", "secuestro")) units += 5;
        if (containsAny(text, "incendio", "fuego", "multitud", "zona")) units += 2;
        if (hasRoadBlockage(text)) units += 2;
        return units;
    }

    private int ambulanceUnits(String text) {
        int units = 1;
        if (containsAny(text, "dos heridos", "2 heridos", "dos personas", "2 personas", "dos lesionados", "2 lesionados")) {
            units = 2;
        }
        if (containsAny(text, "varios heridos", "varias personas", "personas heridas", "multiples heridos", "múltiples heridos", "muchos heridos", "muchas personas", "victimas", "víctimas", "lesionados", "accidentados")) {
            units = 3;
        }
        if (containsAny(text, "mas de 3", "más de 3", "multitud", "bus", "choque multiple", "choque múltiple", "derrumbe", "colapso")) {
            units = Math.max(units, 4);
        }
        return units;
    }

    private int fireTruckUnits(String text) {
        int units = 1;
        if (containsAny(text, "fuego", "llamas", "incendio", "humo")) units += 2;
        if (containsAny(text, "explosion", "gas", "atrapado", "grande")) units += 1;
        return units;
    }

    private int estimateResolveMinutes(String target, String text, boolean high) {
        if (hasLongRoadDisruption(text)) return ROAD_LANDSLIDE_MINUTES;
        if (hasRoadBlockage(text)) return ROAD_BLOCKAGE_MINUTES;
        if (hasMajorRoadIncident(text)) return ROAD_MAJOR_MINUTES;
        if (hasRoadIncident(text) && hasInjurySignal(text)) return ROAD_INJURY_MINUTES;
        if (hasRoadIncident(text)) return ROAD_MINOR_MINUTES;

        if ("BOMBEROS".equals(target)) {
            if (containsAny(text, "explosion", "gas", "atrapado", "colapso", "estructura", "edificio", "grande")) {
                return COMPLEX_FIRE_MINUTES;
            }
            if (containsAny(text, "incendio", "fuego", "llamas", "humo")) {
                return FIRE_MINUTES;
            }
        }

        if ("HOSPITAL".equals(target)) {
            if (containsAny(text, "varios heridos", "varias personas", "multiples", "múltiples", "muchos heridos", "bus", "choque multiple", "choque múltiple")) {
                return ROAD_INJURY_MINUTES;
            }
            return MEDICAL_SINGLE_MINUTES;
        }

        if ("POLICIA".equals(target)) {
            if (containsAny(text, "arma", "disparo", "secuestro", "violencia", "multitud", "disturbio")) {
                return POLICE_HIGH_RISK_MINUTES;
            }
            if (containsAny(text, "robo", "asalto", "hurto", "sospechoso")) {
                return POLICE_STANDARD_MINUTES;
            }
        }

        if (high) return Math.max(highPriorityResolveMinutes, FALLBACK_HIGH_MINUTES);
        return Math.max(defaultResolveMinutes, ROAD_MINOR_MINUTES);
    }

    private boolean hasRoadIncident(String text) {
        return containsAny(text, "accidente", "choque", "colision", "colisión", "moto", "motocicleta", "vehiculo", "vehículo", "carro", "bus", "camion", "camión", "via", "vía", "carretera", "avenida");
    }

    private boolean hasRoadBlockage(String text) {
        return containsAny(text, "via bloqueada", "vía bloqueada", "carretera bloqueada", "bloqueo de via", "bloqueo de vía", "cierre vial", "paso cerrado", "trafico detenido", "tráfico detenido", "obstruccion", "obstrucción", "arbol caido", "árbol caído", "inundacion", "inundación");
    }

    private boolean hasLongRoadDisruption(String text) {
        return containsAny(text, "derrumbe", "deslizamiento", "deslizamiento de tierra", "rocas", "lodo", "hundimiento", "puente caido", "puente caído", "colapso de via", "colapso de vía");
    }

    private boolean hasMajorRoadIncident(String text) {
        return containsAny(text, "choque multiple", "choque múltiple", "varios vehiculos", "varios vehículos", "bus", "camion", "camión", "volcamiento", "atrapado", "atrapados", "explosion", "explosión", "fatal", "muerto", "fallecido");
    }

    private boolean hasInjurySignal(String text) {
        return containsAny(text, "herido", "heridos", "lesionado", "lesionados", "victima", "víctima", "victimas", "víctimas", "inconsciente", "sin respuesta", "no responde", "sangre", "fractura");
    }

    private String formatDuration(int minutes) {
        if (minutes < 60) return minutes + " min";
        int hours = minutes / 60;
        int remainingMinutes = minutes % 60;
        if (remainingMinutes == 0) return hours + " h";
        return hours + " h " + remainingMinutes + " min";
    }

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private int safeUnits(Emergency emergency) {
        return Math.max(Optional.ofNullable(emergency.getAssignedUnits()).orElse(1), 1);
    }

    private int safeInt(Integer value) {
        return Math.max(Optional.ofNullable(value).orElse(0), 0);
    }

    private boolean isEntityActorFor(String targetEntity, JwtService.AuthenticatedUser actor) {
        return actor != null && targetEntity != null && targetEntity.equals(actor.role()) && pools.containsKey(targetEntity);
    }

    private boolean canManageEmergency(Emergency emergency, JwtService.AuthenticatedUser actor) {
        if (!isEntityActorFor(emergency.getTargetEntity(), actor)) return false;
        String assignedUsername = emergency.getAssignedOperatorUsername();
        return assignedUsername == null || assignedUsername.equals(actor.username());
    }

    private ResourceInventory inventoryFor(JwtService.AuthenticatedUser actor) {
        ResourceInventory inventory = inventoryRepository.findByUsername(actor.username()).orElseGet(() -> {
            ResourceInventory createdInventory = new ResourceInventory();
            createdInventory.setUsername(actor.username());
            createdInventory.setTargetEntity(actor.role());
            createdInventory.setTotalUnits(defaultUnitsFor(actor.role()));
            createdInventory.setDailyAddedUnits(0);
            createdInventory.setDailyRemovedUnits(0);
            createdInventory.setDailyLimitDate(LocalDate.now(clock));
            createdInventory.setDefaultResourcesApplied(true);
            return inventoryRepository.save(createdInventory);
        });
        return applyDefaultResourcesToLegacyInventory(inventory, actor.role());
    }

    private ResourceInventory emptyInventory(String targetEntity) {
        ResourceInventory inventory = new ResourceInventory();
        inventory.setUsername("");
        inventory.setTargetEntity(targetEntity);
        inventory.setTotalUnits(0);
        inventory.setDailyAddedUnits(0);
        inventory.setDailyRemovedUnits(0);
        inventory.setDailyLimitDate(LocalDate.now(clock));
        return inventory;
    }

    private void resetDailyLimitIfNeeded(ResourceInventory inventory) {
        LocalDate today = LocalDate.now(clock);
        boolean changed = false;

        if (inventory.getDailyAddedUnits() == null) {
            inventory.setDailyAddedUnits(0);
            changed = true;
        }
        if (inventory.getDailyRemovedUnits() == null) {
            inventory.setDailyRemovedUnits(0);
            changed = true;
        }
        if (inventory.getDailyLimitDate() == null) {
            inventory.setDailyLimitDate(today);
            changed = true;
        }
        if (!today.equals(inventory.getDailyLimitDate())) {
            inventory.setDailyLimitDate(today);
            inventory.setDailyAddedUnits(0);
            inventory.setDailyRemovedUnits(0);
            changed = true;
        }
        if (changed && inventory.getId() != null) {
            inventoryRepository.save(inventory);
        }
    }

    private ResourceInventory applyDefaultResourcesToLegacyInventory(ResourceInventory inventory, String targetEntity) {
        if (Boolean.TRUE.equals(inventory.getDefaultResourcesApplied())) {
            return inventory;
        }

        if (safeInt(inventory.getTotalUnits()) == 0) {
            inventory.setTotalUnits(defaultUnitsFor(targetEntity));
        }
        inventory.setDefaultResourcesApplied(true);
        return inventoryRepository.save(inventory);
    }

    private int defaultUnitsFor(String targetEntity) {
        ResourcePool pool = pools.get(targetEntity);
        return pool == null ? 0 : pool.defaultUnits();
    }

    private ResourceSummary summaryFor(ResourcePool pool, ResourceInventory inventory, int used, int waiting, int inProgress) {
        return new ResourceSummary(
                pool.target(),
                pool.label(),
                pool.unitName(),
                safeInt(inventory.getTotalUnits()),
                used,
                safeInt(inventory.getDailyAddedUnits()),
                dailyAddLimit,
                safeInt(inventory.getDailyRemovedUnits()),
                dailyRemoveLimit,
                waiting,
                inProgress
        );
    }

    private int resolveMinutes(Emergency emergency) {
        return Math.max(Optional.ofNullable(emergency.getEstimatedResolveMinutes()).orElse(defaultResolveMinutes), 1);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record ResourcePool(String target, String label, String unitName, int defaultUnits) {}
    private record ResourceDecision(int units, String resourceLabel, int resolveMinutes) {}
}
