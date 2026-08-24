package com.shop.online_shop.repository;

import com.shop.online_shop.entity.User;
import com.shop.online_shop.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"role", "role.permissions", "sellerProfile"})
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Override
    @EntityGraph(attributePaths = {"role", "sellerProfile"})
    Optional<User> findById(Long id);

    // ==================== فهرست کاربران ====================

    @EntityGraph(attributePaths = {"role", "sellerProfile"})
    Page<User> findByRoleId(Long roleId, Pageable pageable);

    @EntityGraph(attributePaths = {"role", "sellerProfile"})
    Page<User> findByStatus(UserStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"role", "sellerProfile"})
    Page<User> findByRoleIdAndStatus(Long roleId, UserStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"role", "sellerProfile"})
    Page<User> findAll(Pageable pageable);

    // ==================== فروشندگان ====================
    // بر اساس پرچم نقش، نه نام آن — تا نقش‌های سفارشی فروشنده‌محور هم بیایند

    @EntityGraph(attributePaths = {"role", "sellerProfile"})
    @Query("SELECT u FROM User u WHERE u.role.requiresSellerApproval = true")
    Page<User> findSellers(Pageable pageable);

    @EntityGraph(attributePaths = {"role", "sellerProfile"})
    @Query("SELECT u FROM User u WHERE u.role.requiresSellerApproval = true "
         + "AND u.status = :status")
    Page<User> findSellersByStatus(@Param("status") UserStatus status, Pageable pageable);

    // ==================== محافظت ====================

    /** جلوگیری از حذف یا تنزل آخرین ادمین فعال */
    @Query("SELECT COUNT(u) FROM User u WHERE u.role.code = 'ADMIN' AND u.status = 'ACTIVE'")
    long countActiveAdmins();

    @Query("SELECT u.id FROM User u WHERE u.role.id = :roleId")
    List<Long> findIdsByRoleId(@Param("roleId") Long roleId);
}