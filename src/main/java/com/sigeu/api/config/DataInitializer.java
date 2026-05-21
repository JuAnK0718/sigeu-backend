package com.sigeu.api.config;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner init(UserRepository repo, @Value("${sigeu.seed-demo-users:true}") boolean seedDemoUsers) {
        return args -> {
            if (!seedDemoUsers) return;

            createIfMissing(repo, "juan", "123", "CITIZEN", "Juan Perez");
            createIfMissing(repo, "miguel", "123", "CITIZEN", "Miguel Angel");
            createIfMissing(repo, "johan", "123", "CITIZEN", "Johan Guerrero");
            createIfMissing(repo, "policia_pasto", "pasto123", "POLICIA", "Policia Nacional");
            createIfMissing(repo, "bomberos_pasto", "fuego123", "BOMBEROS", "Cuerpo de Bomberos");
            createIfMissing(repo, "hospital_pasto", "salud123", "HOSPITAL", "Hospital Universitario");
        };
    }

    private void createIfMissing(UserRepository repo, String username, String password, String role, String name) {
        if (repo.findByUsername(username).isEmpty()) {
            repo.save(create(username, password, role, name));
        }
    }

    private User create(String username, String password, String role, String name) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(role);
        user.setName(name);
        return user;
    }
}
