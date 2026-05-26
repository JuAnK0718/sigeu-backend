package com.sigeu.api;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.EmergencyRepository;
import com.sigeu.api.repository.ResourceInventoryRepository;
import com.sigeu.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EmergencyControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmergencyRepository emergencyRepository;

    @Autowired
    private ResourceInventoryRepository inventoryRepository;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void cleanDatabase() {
        emergencyRepository.deleteAll();
        inventoryRepository.deleteAll();
    }

    @Test
    void createEmergencyNormalizesTargetAndAssignsResources() throws Exception {
        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "title": "Incidente vial",
                          "description": "Choque con heridos",
                          "location": "1.2136, -77.2811",
                          "type": "ACCIDENT",
                          "targetEntity": "policia"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetEntity").value("POLICIA"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assignedUnits").isNumber())
                .andExpect(jsonPath("$.resourceLabel").value("policias"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(get("/api/emergencies").param("target", "policia")
                .header("Authorization", authHeader("POLICIA", "pol_central")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetEntity").value("POLICIA"))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/emergencies/resources").param("target", "policia")
                .header("Authorization", authHeader("POLICIA", "pol_central")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").value(30))
                .andExpect(jsonPath("$.usedUnits").isNumber())
                .andExpect(jsonPath("$.availableUnits").isNumber());
    }

    @Test
    void updateStatusRejectsInvalidValues() throws Exception {
        var saved = emergencyRepository.save(newEmergency());

        mockMvc.perform(put("/api/emergencies/{id}/status", saved.getId())
                .header("Authorization", authHeader("HOSPITAL", "hospital_central"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "status": "CLOSED"
                        }
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/emergencies/{id}/status", saved.getId())
                .header("Authorization", authHeader("HOSPITAL", "hospital_central"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "status": "resolved"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void createEmergencyWaitsWhenResourcesAreFull() throws Exception {
        String token = authHeader("BOMBEROS", "bomberos_central");
        String incident = """
                {
                  "title": "Incendio grande",
                  "description": "Casa en llamas con humo y explosion",
                  "location": "1.2136, -77.2811",
                  "type": "FIRE",
                  "targetEntity": "BOMBEROS"
                }
                """;

        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(incident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assignedUnits").value(4));

        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(incident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assignedUnits").value(4));

        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(incident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        mockMvc.perform(get("/api/emergencies").param("target", "BOMBEROS")
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("WAITING"));

        mockMvc.perform(get("/api/emergencies/resources").param("target", "BOMBEROS")
                .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").value(8))
                .andExpect(jsonPath("$.usedUnits").value(8))
                .andExpect(jsonPath("$.waitingIncidents").value(1));
    }

    @Test
    void addResourcesRejectsDailyLimitOverflow() throws Exception {
        String token = authHeader("HOSPITAL", "hospital_limit");

        mockMvc.perform(post("/api/emergencies/resources/add")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "target": "HOSPITAL",
                          "units": "10"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").value(25))
                .andExpect(jsonPath("$.remainingDailyAdd").value(0));

        mockMvc.perform(post("/api/emergencies/resources/add")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "target": "HOSPITAL",
                          "units": "1"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createMedicalEmergencyUsesOneAmbulanceForSingleUnconsciousPerson() throws Exception {
        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "title": "Persona inconsciente",
                          "description": "Joven sin respuesta en un sofa. Se necesita HOSPITAL.",
                          "location": "1.2136, -77.2811",
                          "type": "MEDICAL",
                          "targetEntity": "HOSPITAL"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assignedUnits").value(1))
                .andExpect(jsonPath("$.resourceLabel").value("ambulancias"));
    }

    @Test
    void createMedicalEmergencyUsesMoreAmbulancesForMultipleInjuredPeople() throws Exception {
        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "title": "Accidente con varios heridos",
                          "description": "Choque multiple con varias personas heridas y lesionados.",
                          "location": "1.2136, -77.2811",
                          "type": "MEDICAL",
                          "targetEntity": "HOSPITAL"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.assignedUnits").value(4))
                .andExpect(jsonPath("$.resourceLabel").value("ambulancias"));
    }

    @Test
    void createEmergencyRejectsTooLongTitle() throws Exception {
        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "title": "%s",
                          "description": "Choque con heridos",
                          "location": "1.2136, -77.2811",
                          "type": "ACCIDENT",
                          "targetEntity": "POLICIA"
                        }
                        """.formatted("A".repeat(121))))
                .andExpect(status().isBadRequest());
    }

    private com.sigeu.api.model.Emergency newEmergency() {
        var emergency = new com.sigeu.api.model.Emergency();
        emergency.setTitle("Alerta medica");
        emergency.setDescription("Persona herida");
        emergency.setLocation("1.2136, -77.2811");
        emergency.setTargetEntity("HOSPITAL");
        return emergency;
    }

    private String authHeader(String role, String username) {
        User user = new User();
        user.setUsername(username);
        user.setRole(role);
        return "Bearer " + jwtService.generateToken(user);
    }
}
