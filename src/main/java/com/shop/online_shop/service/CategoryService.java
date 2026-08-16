package com.shop.online_shop.service;

import com.shop.online_shop.dto.request.CategoryRequest;
import com.shop.online_shop.dto.response.CategoryResponse;
import com.shop.online_shop.entity.Category;
import com.shop.online_shop.exception.ApiException;
import com.shop.online_shop.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final AuditLogService auditLogService;

    // ==================== خواندن ====================

    /**
     * درخت کامل با یک کوئری ساخته می‌شود تا نیازی به
     * lazy loading خارج از تراکنش نباشد.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getTreeAsResponse() {
        List<Category> all = categoryRepository.findAll();
        Map<Long, List<Category>> byParent = groupByParent(all);

        return all.stream()
                .filter(c -> c.getParent() == null)
                .sorted(Comparator.comparing(Category::getName))
                .map(root -> buildNode(root, byParent))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getByIdAsResponse(Long id) {
        Category category = getById(id);
        return buildNode(category, groupByParent(categoryRepository.findAll()));
    }

    @Transactional(readOnly = true)
    public CategoryResponse getBySlugAsResponse(String slug) {
        Category category = getBySlug(slug);
        return buildNode(category, groupByParent(categoryRepository.findAll()));
    }

    @Transactional(readOnly = true)
    public Category getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("دسته‌بندی یافت نشد"));
    }

    @Transactional(readOnly = true)
    public Category getBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> ApiException.notFound("دسته‌بندی یافت نشد"));
    }

    /**
     * شناسه دسته‌بندی و همه زیرمجموعه‌هایش.
     * برای فیلتر محصولات استفاده می‌شود تا انتخاب یک دسته والد
     * محصولات زیرمجموعه‌ها را هم برگرداند.
     */
    @Transactional(readOnly = true)
    public Set<Long> collectDescendantIds(Long rootId) {
        Set<Long> result = new LinkedHashSet<>();
        Deque<Long> queue = new ArrayDeque<>();
        queue.add(rootId);

        while (!queue.isEmpty()) {
            Long current = queue.poll();
            if (!result.add(current)) {
                continue;
            }
            for (Category child : categoryRepository.findByParentIdOrderByNameAsc(current)) {
                queue.add(child.getId());
            }
        }
        return result;
    }

    // ==================== نوشتن ====================

    @Transactional
    public Category create(CategoryRequest req, Long actorId) {
        Category parent = null;
        int depth = 1;

        if (req.parentId() != null) {
            parent = categoryRepository.findById(req.parentId())
                    .orElseThrow(() -> ApiException.badRequest(
                            "دسته‌بندی والد با شناسه " + req.parentId() + " یافت نشد"));

            depth = parent.getDepth() + 1;

            if (depth > Category.MAX_DEPTH) {
                throw ApiException.badRequest(
                        "حداکثر عمق دسته‌بندی " + Category.MAX_DEPTH + " سطح است");
            }
        }

        Category saved = categoryRepository.save(Category.builder()
                .name(req.name().trim())
                .slug(uniqueSlug(req.name()))
                .description(req.description())
                .parent(parent)
                .depth(depth)
                .build());

        auditLogService.record(actorId, "CATEGORY_CREATED", "id: " + saved.getId());
        return saved;
    }

    @Transactional
    public Category update(Long id, CategoryRequest req, Long actorId) {
        Category category = getById(id);

        category.setName(req.name().trim());
        category.setDescription(req.description());

        auditLogService.record(actorId, "CATEGORY_UPDATED", "id: " + id);
        return categoryRepository.save(category);
    }

    /** حذف فقط اگر نه زیرمجموعه دارد نه محصول */
    @Transactional
    public void delete(Long id, Long actorId) {
        Category category = getById(id);

        if (categoryRepository.existsByParentId(id)) {
            throw ApiException.conflict("این دسته‌بندی زیرمجموعه دارد و قابل حذف نیست");
        }
        if (categoryRepository.hasProducts(id)) {
            throw ApiException.conflict("این دسته‌بندی محصول دارد و قابل حذف نیست");
        }

        categoryRepository.delete(category);
        auditLogService.record(actorId, "CATEGORY_DELETED", "id: " + id);
    }

    // ==================== کمکی ====================

    private Map<Long, List<Category>> groupByParent(List<Category> all) {
        return all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));
    }

    private CategoryResponse buildNode(Category category, Map<Long, List<Category>> byParent) {
        List<CategoryResponse> children = byParent
                .getOrDefault(category.getId(), List.of())
                .stream()
                .sorted(Comparator.comparing(Category::getName))
                .map(child -> buildNode(child, byParent))
                .toList();

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getParent() != null ? category.getParent().getId() : null,
                category.getDepth(),
                children.isEmpty(),
                children
        );
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String candidate = base;
        int suffix = 2;

        while (categoryRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String slugify(String input) {
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^\\p{L}\\p{N}]+", "-")
                .replaceAll("(^-|-$)", "");

        return normalized.isBlank()
                ? UUID.randomUUID().toString().substring(0, 8)
                : normalized;
    }
}