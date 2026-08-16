package com.shop.online_shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.repository.RoleRepository;
import com.shop.online_shop.repository.UserRepository;
import com.shop.online_shop.security.JwtService;
import com.shop.online_shop.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * پایه مشترک تست‌های یکپارچه.
 * هر تست در تراکنش خودش اجرا و در پایان rollback می‌شود،
 * پس تست‌ها روی هم اثر نمی‌گذارند.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;

    @Autowired protected UserRepository userRepository;
    @Autowired protected RoleRepository roleRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;

    protected static final String DEFAULT_PASSWORD = "Test@12345";

    protected User customer;
    protected User seller;
    protected User manager;
    protected User admin;

    @BeforeEach
    void setUpUsers() {
        customer = createUser("customer@test.local", "مشتری تست", "USER", UserStatus.ACTIVE);
        seller   = createUser("seller@test.local",   "فروشنده تست", "SELLER", UserStatus.ACTIVE);
        manager  = createUser("manager@test.local",  "مدیر تست",   "MANAGER", UserStatus.ACTIVE);
        admin    = createUser("owner@test.local",    "ادمین تست",  "ADMIN", UserStatus.ACTIVE);
    }

    protected User createUser(String email, String fullName, String roleName,
                              UserStatus status) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));

        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .fullName(fullName)
                .phone("09120000000")
                .role(role)
                .status(status)
                .build());
    }

    /** هدر Authorization آماده برای یک کاربر */
    protected String bearerFor(User user) {
        return "Bearer " + jwtService.generateAccessToken(UserPrincipal.from(user));
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}