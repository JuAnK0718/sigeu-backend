package com.sigeu.api.config;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner initDatabase(UserRepository repository) {
        return args -> {
            if (repository.count() == 0) {
                // Crear usuario Policía
                User police = new User();
                police.setUsername("admin_policia");
                police.setPassword("12345");
                police.setRole("POLICE");
                police.setName("Comando Pasto");
                repository.save(police);

                // Crear un Ciudadano de prueba
                User citizen = new User();
                citizen.setUsername("dania");
                citizen.setPassword("dania123");
                citizen.setRole("CITIZEN");
                citizen.setName("Dania");
                repository.save(citizen);

                System.out.println("✅ Usuarios de prueba creados.");
            }
        };
    }
}