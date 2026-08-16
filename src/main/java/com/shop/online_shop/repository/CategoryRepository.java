package com.shop.online_shop.repository;

import com.shop.online_shop.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<Category> findByParentIsNullOrderByNameAsc();

    List<Category> findByParentIdOrderByNameAsc(Long parentId);

    boolean existsByParentId(Long parentId);

    @Query("SELECT COUNT(p) > 0 FROM Product p WHERE p.category.id = :categoryId")
    boolean hasProducts(@Param("categoryId") Long categoryId);
}