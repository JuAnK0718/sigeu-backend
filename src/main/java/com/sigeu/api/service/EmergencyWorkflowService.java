package com.sigeu.api.service;

import com.sigeu.api.dto.ResourceSummary;
import com.sigeu.api.model.Emergency;
import com.sigeu.api.repository.EmergencyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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

    private final EmergencyRepository repository;
    private final Clock clock;
    private final boolean automationEnabled;
    private final int defaultResolveMinutes;
    private final int highPriorityResolveMinutes;
    private final int deleteAfterResolveMinutes;
    private final Map<String, ResourcePool> pools;

    public EmergencyWorkflowService(
            EmergencyRepository repository,
            Clock clock,
            @Value("${sigeu.workflow.enabled:true}") boolean automationEnabled,
            @Value("${sigeu.workflow.resolve-minutes:15}") int defaultResolveMinutes,
            @Value("${sigeu.workflow.high-priority-resolve-minutes:10}") int highPriorityResolveMinutes,
            @Value("${sigeu.workflow.delete-after-resolve-minutes:10}") int deleteAfterResolveMinutes,
            @Value("${sigeu.resources.policia:30}") int policeUnits,
            @Value("${sigeu.resources.hospital:15}") int ambulanceUnits,
            @Value("${sigeu.resources.bomberos:8}") int fireTruckUnits
    ) {
        this.repository = repository;
        this.clock = clock;
        this.automationEnabled = automationEnabled;
        this.defaultResolveMinutes = defaultResolveMinutes;
        this.highPriorityResolveMinutes = highPriorityResolveMinutes;
        this.deleteAfterResolveMinutes = deleteAfterResolveMinutes;
        this.pools = Map.of(
                "POLICIA", new ResourcePool("POLICIA", "Policia", "policias", policeUnits),
                "HOSPITAL", new ResourcePool("HOSPITAL", "Hospital", "ambulancias", ambulanceUnits),
                "BOMBEROS", new ResourcePool("BOMBEROS", "Bomberos", "camiones", fireTruckUnits)
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
        runAutomationCycle();
        return repository.findById(saved.getId()).orElse(saved);
    }

    @Transactional
    public synchronized Optional<Emergency> updateStatus(Long id, String status) {
        runAutomationCycle();
        return repository.findById(id).map(emergency -> {
            LocalDateTime now = now();

            if (IN_PROGRESS.equals(status)) {
                ensureDecision(emergency);
                if (!canAssign(emergency)) {
                    emergency.setStatus(WAITING);
                    emergency.setOperationalNote("Sin " + emergency.getResourceLabel() + " disponibles. Caso en espera automatica.");
                    return repository.save(emergency);
                }
                assign(emergency, now);
                return repository.save(emergency);
            }

            if (RESOLVED.equals(status)) {
                resolve(emergency, now);
                return repository.save(emergency);
            }

            emergency.setStatus(status);
            if (PENDING.equals(status) || WAITING.equals(status)) {
                emergency.setAutoStartedAt(null);
                emergency.setAutoResolveAt(null);
                emergency.setAutoDeleteAt(null);
            }
            return repository.save(emergency);
        });
    }

    @Transactional
    public synchronized ResourceSummary resourcesFor(String targetEntity) {
        runAutomationCycle();
        ResourcePool pool = pools.get(targetEntity);
        if (pool == null) return null;

        int used = usedUnits(targetEntity);
        int waiting = repository.findByTargetEntityAndStatus(targetEntity, WAITING).size();
        int inProgress = repository.findByTargetEntityAndStatus(targetEntity, IN_PROGRESS).size();
        return new ResourceSummary(pool.target(), pool.label(), pool.unitName(), pool.totalUnits(), used, waiting, inProgress);
    }

    @Scheduled(fixedDelayString = "${sigeu.workflow.tick-ms:60000}")
    @Transactional
    public synchronized void runAutomationCycle() {
        if (!automationEnabled) return;

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

        List<Emergency> candidates = repository.findByStatusOrderByCreatedAtAsc(WAITING);
        candidates.addAll(repository.findByStatusOrderByCreatedAtAsc(PENDING));
        candidates.sort(Comparator.comparing(Emergency::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

        for (Emergency emergency : candidates) {
            ensureDecision(emergency);
            if (canAssign(emergency)) {
                assign(emergency, now);
            } else {
                emergency.setStatus(WAITING);
                emergency.setOperationalNote("Sin " + emergency.getResourceLabel() + " disponibles. Caso en espera automatica.");
            }
            repository.save(emergency);
        }
    }

    private void assign(Emergency emergency, LocalDateTime now) {
        emergency.setStatus(IN_PROGRESS);
        emergency.setAutoStartedAt(now);
        emergency.setAutoResolveAt(now.plusMinutes(resolveMinutes(emergency)));
        emergency.setAutoDeleteAt(null);
        emergency.setOperationalNote("Despacho automatico: " + emergency.getAssignedUnits() + " " + emergency.getResourceLabel()
                + ". Resolucion estimada en " + resolveMinutes(emergency) + " min.");
    }

    private void resolve(Emergency emergency, LocalDateTime now) {
        emergency.setStatus(RESOLVED);
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

    private boolean canAssign(Emergency emergency) {
        ResourcePool pool = pools.get(emergency.getTargetEntity());
        if (pool == null) return false;
        return usedUnits(emergency.getTargetEntity()) + safeUnits(emergency) <= pool.totalUnits();
    }

    private int usedUnits(String targetEntity) {
        return repository.findByTargetEntityAndStatus(targetEntity, IN_PROGRESS).stream()
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

        units = Math.max(1, Math.min(units, pool.totalUnits()));
        int minutes = high ? highPriorityResolveMinutes : defaultResolveMinutes;
        if (containsAny(text, "explosion", "incendio", "multiples", "varios", "grave")) {
            minutes = Math.max(highPriorityResolveMinutes, minutes + 5);
        }
        return new ResourceDecision(units, pool.unitName(), minutes);
    }

    private int policeUnits(String text) {
        int units = 2;
        if (containsAny(text, "robo", "asalto", "violencia")) units += 3;
        if (containsAny(text, "arma", "disparo", "secuestro")) units += 5;
        if (containsAny(text, "incendio", "fuego", "multitud", "zona")) units += 2;
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

    private boolean containsAny(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) return true;
        }
        return false;
    }

    private int safeUnits(Emergency emergency) {
        return Math.max(Optional.ofNullable(emergency.getAssignedUnits()).orElse(1), 1);
    }

    private int resolveMinutes(Emergency emergency) {
        return Math.max(Optional.ofNullable(emergency.getEstimatedResolveMinutes()).orElse(defaultResolveMinutes), 1);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record ResourcePool(String target, String label, String unitName, int totalUnits) {}
    private record ResourceDecision(int units, String resourceLabel, int resolveMinutes) {}
}
