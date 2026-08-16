package com.shop.online_shop.repository;

import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    Page<User> findByRoleName(String roleName, Pageable pageable);

    Page<User> findByRoleNameAndStatus(String roleName, UserStatus status, Pageable pageable);

    /** برای محافظت از آخرین ادمین */
    @Query("SELECT COUNT(u) FROM User u WHERE u.role.name = 'ADMIN' AND u.status = 'ACTIVE'")
    long countActiveAdmins();

    /** همه کاربران یک نقش — برای ابطال توکن پس از تغییر مجوزها */
    @Query("SELECT u.id FROM User u WHERE u.role.id = :roleId")
    java.util.List<Long> findIdsByRoleId(@Param("roleId") Long roleId);
}