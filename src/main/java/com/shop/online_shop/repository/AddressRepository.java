package com.shop.online_shop.repository;

import com.shop.online_shop.entity.Address;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    List<Address> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    /** همیشه با userId جستجو می‌شود تا دسترسی به آدرس دیگران ممکن نباشد */
    Optional<Address> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);

    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.user.id = :userId")
    void clearDefaultFlags(@Param("userId") Long userId);

    Page<Address> findByUserId(Long userId, Pageable pageable);
}