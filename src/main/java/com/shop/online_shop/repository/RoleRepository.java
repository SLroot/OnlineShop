package com.shop.online_shop.repository;

import com.shop.online_shop.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCode(String code);

    Optional<Role> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    /** برای بررسی یکتایی نام هنگام ویرایش یک نقش موجود */
    boolean existsByNameAndIdNot(String name, Long id);

    /** نقش‌هایی که در ثبت‌نام آزاد قابل انتخاب‌اند */
    List<Role> findByOpenRegistrationTrue();

    /** برای جلوگیری از حذف نقشی که کاربر دارد */
    @Query("SELECT COUNT(u) FROM User u WHERE u.role.id = :roleId")
    long countUsers(@Param("roleId") Long roleId);

    /** برای ابطال نشست‌ها پس از تغییر مجوزهای یک نقش */
    @Query("SELECT u.id FROM User u WHERE u.role.id = :roleId")
    List<Long> findUserIds(@Param("roleId") Long roleId);
}