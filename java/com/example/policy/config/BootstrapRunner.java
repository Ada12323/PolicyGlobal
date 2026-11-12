package com.example.policy.config;

import com.example.policy.entity.Role;
import com.example.policy.entity.User;
import com.example.policy.repository.RoleRepository;
import com.example.policy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class BootstrapRunner implements CommandLineRunner {

    @Value("${security.bootstrap-admin:false}")
    private boolean bootstrapAdmin;

    @Value("${security.bootstrap-admin-username:admin}")
    private String adminUsername;

    @Value("${security.bootstrap-admin-password:admin123}")
    private String adminPassword;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapRunner(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!bootstrapAdmin) return;
        // Ensure ADMIN role exists
        Role adminRole = roleRepository.findByCode("ADMIN").orElseGet(() -> {
            Role r = new Role();
            r.setCode("ADMIN");
            r.setName("管理员");
            return roleRepository.save(r);
        });
        userRepository.findByUsername(adminUsername).ifPresentOrElse(u -> {
            // already exists
            System.out.println("[BOOTSTRAP] Admin user exists: " + adminUsername);
        }, () -> {
            User u = User.builder()
                    .username(adminUsername)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .nickname("管理员")
                    .status(1)
                    .roles(Collections.singleton(adminRole))
                    .build();
            userRepository.save(u);
            System.out.println("[BOOTSTRAP] Created admin user: " + adminUsername);
        });
    }
}

