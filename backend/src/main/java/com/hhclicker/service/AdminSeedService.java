package com.hhclicker.service;

import com.hhclicker.config.AdminSeedProperties;
import com.hhclicker.entity.User;
import com.hhclicker.enumeration.UserRole;
import com.hhclicker.enumeration.UserStatus;
import com.hhclicker.repository.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableConfigurationProperties(AdminSeedProperties.class)
public class AdminSeedService implements ApplicationRunner {
    private final AdminSeedProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public AdminSeedService(AdminSeedProperties properties, UserRepository users, PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.getEmail() == null || properties.getEmail().isBlank() || properties.getPassword() == null || properties.getPassword().isBlank()) {
            return;
        }
        String email = properties.getEmail().strip().toLowerCase();
        User user = users.findByEmail(email).orElseGet(User::new);
        user.setEmail(email);
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(properties.getPassword()));
        }
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setCoverLetterGenerationEnabled(true);
        users.save(user);
    }
}
