package com.shop.online_shop.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories", indexes = {
    @Index(name = "idx_category_slug",   columnList = "slug"),
    @Index(name = "idx_category_parent", columnList = "parent_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class Category {

    /** حداکثر عمق مجاز — ریشه سطح ۱ است */
    public static final int MAX_DEPTH = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @OneToMany(mappedBy = "parent")
    @Builder.Default
    private List<Category> children = new ArrayList<>();

    /** سطح در سلسله‌مراتب: ۱ برای ریشه */
    @Column(nullable = false)
    @Builder.Default
    private Integer depth = 1;

    /** فقط دسته‌بندی برگ می‌تواند محصول داشته باشد */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }
}
