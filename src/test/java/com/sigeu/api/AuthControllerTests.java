package com.sigeu.api;

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

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void registerAcceptsFrontendFullNameAndNormalizesUserData() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "Operador_01",
                          "password": "abc123",
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
    }

    @Test
    void loginDoesNotExposePassword() throws Exception {
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
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "username": "JUAN",
                          "password": "123"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("juan"))
                .andExpect(jsonPath("$.role").value("CITIZEN"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
