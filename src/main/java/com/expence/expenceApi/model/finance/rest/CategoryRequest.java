package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.CategoryKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Write model for a category. {@code kind} is immutable after creation: an update reads
 * the name and ignores the kind, because moving a category between INCOME and EXPENSE
 * would leave every transaction already filed under it mismatched with its type.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryRequest {

    @NotBlank
    @Size(max = 60)
    private String name;

    @NotNull
    private CategoryKind kind;
}
