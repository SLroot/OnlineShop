package com.shop.online_shop.config;

import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.repository.RoleRepository;
import com.shop.online_shop.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(2)                       // پس از DataSeeder اجرا می‌شود
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        String email = adminEmail.toLowerCase().trim();

        if (userRepository.existsByEmail(email)) {
            log.info("Admin user already exists, skipping.");
            return;
        }

        // نقش با code جستجو می‌شود نه name، چون name قابل ویرایش است
        roleRepository.findByCode(RoleCode.ADMIN).ifPresentOrElse(
            adminRole -> {
                userRepository.save(User.builder()
                        .email(email)
                        .password(passwordEncoder.encode(adminPassword))
                        .fullName("مدیر سیستم")
                        .role(adminRole)
                        .status(UserStatus.ACTIVE)
                        .mustChangePassword(false)
                        .build());

                log.info("Admin user created: {}", email);
            },
            () -> log.error("Base role {} not found — admin user was not created",
                    RoleCode.ADMIN)
        );
    }
}