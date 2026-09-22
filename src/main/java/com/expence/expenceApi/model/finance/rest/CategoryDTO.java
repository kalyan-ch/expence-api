package com.expence.expenceApi.model.finance.rest;

import com.expence.expenceApi.model.finance.CategoryKind;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CategoryDTO {

    private UUID id;
    private String name;
    private CategoryKind kind;

    /** Named explicitly: Jackson strips the "is" from a boolean getter and would emit "default". */
    @JsonProperty("isDefault")
    private boolean isDefault;
}
