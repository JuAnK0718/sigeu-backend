package com.sigeu.api;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.ResourceInventoryRepository;
import com.sigeu.api.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResourceInventoryRepository inventoryRepository;

    @BeforeEach
    void cleanDatabase() {
        inventoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registerAcceptsFrontendFullNameAndNormalizesUserData() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "Operador_01",
                          "password": "Abc12345",
                          "role": "policia",
                          "fullName": "Unidad Norte"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(content().string("Usuario creado exitosamente"));

        var savedUser = userRepository.findByUsername("operador_01");

        assertThat(savedUser).isPresent();
        assertThat(savedUser.get().getRole()).isEqualTo("POLICIA");
        assertThat(savedUser.get().getName()).isEqualTo("Unidad Norte");
        assertThat(savedUser.get().getPassword()).isNotEqualTo("Abc12345");
        assertThat(savedUser.get().getPassword()).startsWith("$2");

        var inventory = inventoryRepository.findByUsername("operador_01");
        assertThat(inventory).isPresent();
        assertThat(inventory.get().getTotalUnits()).isZero();
        assertThat(inventory.get().getDefaultResourcesApplied()).isTrue();
    }

    @Test
    void loginDoesNotExposePassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "juan",
                          "password": "Abc12345",
                          "role": "CITIZEN",
                          "name": "Juan"
                        }
                        """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "JUAN",
                          "password": "Abc12345"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("juan"))
                .andExpect(jsonPath("$.role").value("CITIZEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void registerRejectsInvalidPassword() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "juan",
                          "password": "123",
                          "role": "CITIZEN",
                          "name": "Juan"
                        }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginUpgradesLegacyPlainTextPassword() throws Exception {
        User legacyUser = new User();
        legacyUser.setUsername("legacy");
        legacyUser.setPassword("Abc12345");
        legacyUser.setRole("CITIZEN");
        legacyUser.setName("Legacy User");
        userRepository.save(legacyUser);

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "legacy",
                          "password": "Abc12345"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        var upgradedUser = userRepository.findByUsername("legacy");
        assertThat(upgradedUser).isPresent();
        assertThat(upgradedUser.get().getPassword()).isNotEqualTo("Abc12345");
        assertThat(upgradedUser.get().getPassword()).startsWith("$2");
    }
}
