package com.ecommerce.product.service;

import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.dto.CreateCategoryRequest;
import com.ecommerce.product.dto.PageResponse;
import com.ecommerce.product.dto.UpdateCategoryRequest;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.CategoryStatus;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.InvalidResourceStateException;
import com.ecommerce.product.exception.ResourceNotFoundException;
import com.ecommerce.product.mapper.CategoryMapper;
import com.ecommerce.product.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Transactional
    public CategoryResponse createCategory(CreateCategoryRequest request) {
        // Friendly pre-check; uk_categories_name is the real guarantee under concurrency.
        if (categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("A category named '%s' already exists".formatted(request.name()));
        }
        Category saved = categoryRepository.save(categoryMapper.toEntity(request));
        log.info("Created category id={}", saved.getId());
        return categoryMapper.toResponse(saved);
    }

    public CategoryResponse getCategory(UUID id) {
        return categoryMapper.toResponse(findCategory(id));
    }

    public PageResponse<CategoryResponse> listCategories(CategoryStatus status, Pageable pageable) {
        Page<Category> page = (status == null)
                ? categoryRepository.findAll(pageable)
                : categoryRepository.findAllByStatus(status, pageable);
        return PageResponse.from(page.map(categoryMapper::toResponse));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        Category category = findCategory(id);
        requireActive(category);

        // Renaming to a name another category already holds must be rejected, but renaming a
        // category to its own current name is a valid no-op.
        if (!category.getName().equals(request.name()) && categoryRepository.existsByName(request.name())) {
            throw new DuplicateResourceException("A category named '%s' already exists".formatted(request.name()));
        }

        category.updateDetails(request.name(), request.description());
        // Flush so @LastModifiedDate and @Version are current before the response is built.
        return categoryMapper.toResponse(categoryRepository.saveAndFlush(category));
    }

    /** Soft delete, idempotent. Existing products keep their category; new ones cannot use it. */
    @Transactional
    public void deactivateCategory(UUID id) {
        Category category = findCategory(id);
        if (category.isActive()) {
            category.deactivate();
            log.info("Deactivated category id={}", id);
        }
    }

    /** Shared with ProductService: resolving a category for a product must reject inactive ones. */
    Category findCategory(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }

    private static void requireActive(Category category) {
        if (!category.isActive()) {
            throw new InvalidResourceStateException(
                    "Category '%s' is inactive and cannot be modified".formatted(category.getId()));
        }
    }
}
