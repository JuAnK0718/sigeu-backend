package com.sigeu.api.config;

import com.sigeu.api.model.User;
import com.sigeu.api.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataInitializer {
    @Bean
    CommandLineRunner init(UserRepository repo) {
        return args -> {
            repo.deleteAll();
            repo.save(create("juan", "123", "CITIZEN", "Juan Pérez"));
            repo.save(create("miguel", "123", "CITIZEN", "Miguel Ángel"));
            repo.save(create("johan", "123", "CITIZEN", "Johan Guerrero"));
            repo.save(create("policia_pasto", "pasto123", "Policia", "Policía Nacional"));
            repo.save(create("bomberos_pasto", "fuego123", "Bomberos", "Cuerpo de Bomberos"));
            repo.save(create("hospital_pasto", "salud123", "Hospital", "Hospital Universitario"));
        };
    }
    private User create(String u, String p, String r, String n) {
        User user = new User();
        user.setUsername(u); user.setPassword(p); user.setRole(r); user.setName(n);
        return user;
    }
}