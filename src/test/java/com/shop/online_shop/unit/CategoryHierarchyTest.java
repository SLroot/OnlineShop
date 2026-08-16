package com.shop.online_shop.unit;

import com.shop.online_shop.entity.Category;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("سلسله‌مراتب دسته‌بندی")
class CategoryHierarchyTest {

    @Test
    @DisplayName("دسته‌بندی بدون زیرمجموعه، برگ است")
    void categoryWithoutChildrenIsLeaf() {
        Category leaf = Category.builder()
                .name("ایسوس").slug("asus").depth(2).build();

        assertThat(leaf.isLeaf()).isTrue();
    }

    @Test
    @DisplayName("دسته‌بندی با زیرمجموعه، برگ نیست")
    void categoryWithChildrenIsNotLeaf() {
        Category child = Category.builder()
                .name("ایسوس").slug("asus").depth(2).build();

        Category parent = Category.builder()
                .name("لپ‌تاپ").slug("laptop").depth(1)
                .children(List.of(child))
                .build();

        assertThat(parent.isLeaf()).isFalse();
    }

    @Test
    @DisplayName("سقف عمق سه سطح است")
    void maxDepthIsThree() {
        assertThat(Category.MAX_DEPTH).isEqualTo(3);
    }
}