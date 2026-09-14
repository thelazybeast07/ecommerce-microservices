package com.ecommerce.product.controller;

import com.ecommerce.product.dto.CategoryResponse;
import com.ecommerce.product.dto.CreateCategoryRequest;
import com.ecommerce.product.dto.PageResponse;
import com.ecommerce.product.dto.UpdateCategoryRequest;
import com.ecommerce.product.entity.CategoryStatus;
import com.ecommerce.product.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Product category management")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create a category")
    @ApiResponse(responseCode = "201", description = "Created; Location header points to it")
    @ApiResponse(responseCode = "409", description = "A category with this name already exists")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse created = categoryService.createCategory(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    @Operation(summary = "List categories (paged)")
    public PageResponse<CategoryResponse> listCategories(
            @RequestParam(required = false) CategoryStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC)
            Pageable pageable) {
        return categoryService.listCategories(status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a category by id")
    public CategoryResponse getCategory(@PathVariable UUID id) {
        return categoryService.getCategory(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    @ApiResponse(responseCode = "409", description = "Name taken, category inactive, or concurrent modification")
    public CategoryResponse updateCategory(@PathVariable UUID id,
                                           @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a category (soft delete, idempotent)")
    public void deactivateCategory(@PathVariable UUID id) {
        categoryService.deactivateCategory(id);
    }
}
