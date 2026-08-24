package com.shop.online_shop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.online_shop.entity.Permission;
import com.shop.online_shop.entity.Role;
import com.shop.online_shop.entity.RoleCode;
import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import com.shop.online_shop.repository.PermissionRepository;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * پایه مشترک تست‌های یکپارچه.
 * هر تست در تراکنش خودش اجرا و در پایان بازگردانی می‌شود،
 * بنابراین تست‌ها روی یکدیگر اثر نمی‌گذارند.
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
    @Autowired protected PermissionRepository permissionRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;

    protected static final String DEFAULT_PASSWORD = "Test@12345";

    protected User customer;
    protected User seller;
    protected User manager;
    protected User admin;

    @BeforeEach
    void setUpUsers() {
        customer = createUser("customer@test.local", "مشتری تست",
                RoleCode.USER, UserStatus.ACTIVE);
        seller = createUser("seller@test.local", "فروشنده تست",
                RoleCode.SELLER, UserStatus.ACTIVE);
        manager = createUser("manager@test.local", "مدیر تست",
                RoleCode.MANAGER, UserStatus.ACTIVE);
        admin = createUser("owner@test.local", "ادمین تست",
                RoleCode.ADMIN, UserStatus.ACTIVE);
    }

    // ==================== ساخت کاربر ====================

    protected User createUser(String email, String fullName, String roleCode,
                              UserStatus status) {
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleCode));

        return saveUser(email, fullName, role, status);
    }

    protected User createUserWithRole(String email, String fullName, Role role) {
        return saveUser(email, fullName, role, UserStatus.ACTIVE);
    }

    private User saveUser(String email, String fullName, Role role, UserStatus status) {
        return userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(DEFAULT_PASSWORD))
                .fullName(fullName)
                .phone("09120000000")
                .role(role)
                .status(status)
                .build());
    }

    // ==================== ساخت نقش سفارشی ====================

    /** نقش سفارشی با مجموعه‌ای دلخواه از مجوزها */
    protected Role createCustomRole(String name, String... permissionNames) {
        Set<Permission> permissions = new HashSet<>(
                permissionRepository.findAll().stream()
                        .filter(p -> Arrays.asList(permissionNames).contains(p.getName()))
                        .toList());

        return roleRepository.save(Role.builder()
                .code(null)
                .name(name)
                .description("نقش آزمایشی")
                .permissions(permissions)
                .systemRole(false)
                .requiresSellerApproval(false)
                .openRegistration(false)
                .build());
    }

    protected Long permissionId(String name) {
        return permissionRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException("Permission not found: " + name))
                .getId();
    }

    protected Long roleId(String code) {
        return roleRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException("Role not found: " + code))
                .getId();
    }

    // ==================== کمکی ====================

    protected String bearerFor(User user) {
        return "Bearer " + jwtService.generateAccessToken(UserPrincipal.from(user));
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }
}