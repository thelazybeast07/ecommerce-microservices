package com.ecommerce.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PUT semantics: the full set of updatable fields. Status is changed via DELETE, not here. */
public record UpdateCategoryRequest(

        @Schema(example = "Footwear")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(example = "Shoes, sandals and boots")
        @Size(max = 1000)
        String description
) {
}
