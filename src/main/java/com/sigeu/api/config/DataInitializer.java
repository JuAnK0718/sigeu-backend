package com.sigeu.api.config;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initUsers(UserRepository repository) {
        return args -> {
            // Solo creamos los usuarios si la base de datos está vacía
            if (repository.count() == 0) {

                // --- CIUDADANOS ---
                repository.save(createUser("juan", "123", "CITIZEN", "Juan Pérez"));
                repository.save(createUser("miguel", "123", "CITIZEN", "Miguel Ángel"));
                repository.save(createUser("johan", "123", "CITIZEN", "Johan Guerrero"));

                // --- ENTIDADES ---
                repository.save(createUser("policia_pasto", "pasto123", "POLICE", "Policía Nacional - Pasto"));
                repository.save(createUser("bomberos_pasto", "fuego123", "FIREFIGHTERS", "Cuerpo de Bomberos"));
                repository.save(createUser("hospital_pasto", "salud123", "HOSPITAL", "Hospital Universitario Departamental"));

                System.out.println("✅ Usuarios para el proyecto final cargados con éxito.");
            }
        };
    }

    // Método auxiliar para no repetir código
    private User createUser(String username, String password, String role, String name) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(password);
        u.setRole(role);
        u.setName(name);
        return u;
    }
}