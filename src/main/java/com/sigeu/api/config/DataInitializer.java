package com.sigeu.api.config;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.UserRepository;
import com.sigeu.api.security.PasswordService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner init(
            UserRepository repo,
            PasswordService passwordService,
            @Value("${sigeu.seed-demo-users:true}") boolean seedDemoUsers
    ) {
        return args -> {
            if (!seedDemoUsers) return;

            createIfMissing(repo, passwordService, "juan", "123", "CITIZEN", "Juan Perez");
            createIfMissing(repo, passwordService, "miguel", "123", "CITIZEN", "Miguel Angel");
            createIfMissing(repo, passwordService, "johan", "123", "CITIZEN", "Johan Guerrero");
            createIfMissing(repo, passwordService, "policia_pasto", "pasto123", "POLICIA", "Policia Nacional");
            createIfMissing(repo, passwordService, "bomberos_pasto", "fuego123", "BOMBEROS", "Cuerpo de Bomberos");
            createIfMissing(repo, passwordService, "hospital_pasto", "salud123", "HOSPITAL", "Hospital Universitario");
        };
    }

    private void createIfMissing(UserRepository repo, PasswordService passwordService, String username, String password, String role, String name) {
        if (repo.findByUsername(username).isEmpty()) {
            repo.save(create(passwordService, username, password, role, name));
        }
    }

    private User create(PasswordService passwordService, String username, String password, String role, String name) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordService.hash(password));
        user.setRole(role);
        user.setName(name);
        return user;
    }
}
