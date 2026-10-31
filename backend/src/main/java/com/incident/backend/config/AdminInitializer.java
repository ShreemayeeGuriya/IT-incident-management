package com.incident.backend.config;

import com.incident.backend.model.Role;
import com.incident.backend.model.User;
import com.incident.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Creates the first ADMIN on startup, because nobody can promote a user until one admin exists.
@Component
public class AdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String name;
    private final String email;
    private final String password;

    public AdminInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                            @Value("${app.admin.name}") String name,
                            @Value("${app.admin.email}") String email,
                            @Value("${app.admin.password}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.name = name;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (!userRepository.existsByRole(Role.ADMIN) && !userRepository.existsByEmail(email)) {
            User admin = new User();
            admin.setName(name);
            admin.setEmail(email);
            admin.setPasswordHash(passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        }
    }
}
