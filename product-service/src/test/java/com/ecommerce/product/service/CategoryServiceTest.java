package com.ecommerce.product.service;

import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.dto.CreateCategoryRequest;
import com.ecommerce.product.dto.UpdateCategoryRequest;
import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.CategoryStatus;
import com.ecommerce.product.exception.DuplicateResourceException;
import com.ecommerce.product.exception.InvalidResourceStateException;
import com.ecommerce.product.mapper.CategoryMapper;
import com.ecommerce.product.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    private CategoryService categoryService;

    private final UUID categoryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, new CategoryMapper());
    }

    @Test
    void createCategory_duplicateName_isRejected() {
        when(categoryRepository.existsByName("Footwear")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(new CreateCategoryRequest("Footwear", null)))
                .isInstanceOf(DuplicateResourceException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("renaming a category to its own current name is allowed, not a duplicate")
    void updateCategory_sameName_isAllowed() {
        Category category = existingCategory();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.saveAndFlush(category)).thenReturn(category);

        CategoryResponse response = categoryService.updateCategory(categoryId,
                new UpdateCategoryRequest("Footwear", "Updated description"));

        assertThat(response.description()).isEqualTo("Updated description");
        verify(categoryRepository, never()).existsByName(any());
    }

    @Test
    void updateCategory_nameTakenByAnother_isRejected() {
        Category category = existingCategory();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByName("Apparel")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId,
                new UpdateCategoryRequest("Apparel", null)))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void updateCategory_inactiveCategory_isRejected() {
        Category category = existingCategory();
        category.deactivate();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> categoryService.updateCategory(categoryId,
                new UpdateCategoryRequest("Apparel", null)))
                .isInstanceOf(InvalidResourceStateException.class);
    }

    @Test
    void deactivateCategory_marksInactive() {
        Category category = existingCategory();
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        categoryService.deactivateCategory(categoryId);

        assertThat(category.getStatus()).isEqualTo(CategoryStatus.INACTIVE);
    }

    private Category existingCategory() {
        Category category = new Category("Footwear", "Shoes and boots");
        ReflectionTestUtils.setField(category, "id", categoryId);
        return category;
    }
}
