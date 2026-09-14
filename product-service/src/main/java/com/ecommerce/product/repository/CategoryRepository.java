package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Category;
import com.ecommerce.product.entity.CategoryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    boolean existsByName(String name);

    Page<Category> findAllByStatus(CategoryStatus status, Pageable pageable);
}
