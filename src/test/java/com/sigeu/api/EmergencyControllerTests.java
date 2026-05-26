package com.sigeu.api;

import com.sigeu.api.repository.EmergencyRepository;
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

    @BeforeEach
    void cleanDatabase() {
        emergencyRepository.deleteAll();
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
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedUnits").isNumber())
                .andExpect(jsonPath("$.resourceLabel").value("policias"))
                .andExpect(jsonPath("$.autoResolveAt").exists())
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(get("/api/emergencies").param("target", "policia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetEntity").value("POLICIA"));

        mockMvc.perform(get("/api/emergencies/resources").param("target", "policia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").value(30))
                .andExpect(jsonPath("$.usedUnits").isNumber())
                .andExpect(jsonPath("$.availableUnits").isNumber());
    }

    @Test
    void updateStatusRejectsInvalidValues() throws Exception {
        var saved = emergencyRepository.save(newEmergency());

        mockMvc.perform(put("/api/emergencies/{id}/status", saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "status": "CLOSED"
                        }
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/emergencies/{id}/status", saved.getId())
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
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedUnits").value(4));

        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(incident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.assignedUnits").value(4));

        mockMvc.perform(post("/api/emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content(incident))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING"));

        mockMvc.perform(get("/api/emergencies/resources").param("target", "BOMBEROS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUnits").value(8))
                .andExpect(jsonPath("$.usedUnits").value(8))
                .andExpect(jsonPath("$.waitingIncidents").value(1));
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
}
