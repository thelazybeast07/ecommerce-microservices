package com.ecommerce.product.mapper;

import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.dto.CreateCategoryRequest;
import com.ecommerce.product.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public Category toEntity(CreateCategoryRequest request) {
        return new Category(request.name(), request.description());
    }

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getStatus(),
                category.getCreatedAt(),
                category.getUpdatedAt());
    }
}
