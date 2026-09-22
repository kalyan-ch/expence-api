package com.expence.expenceApi.service;

import com.expence.expenceApi.dao.CategoryRepository;
import com.expence.expenceApi.dao.TransactionRepository;
import com.expence.expenceApi.exception.ConflictException;
import com.expence.expenceApi.exception.NotFoundException;
import com.expence.expenceApi.model.finance.Category;
import com.expence.expenceApi.model.finance.CategoryKind;
import com.expence.expenceApi.model.finance.rest.CategoryDTO;
import com.expence.expenceApi.model.finance.rest.CategoryRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<CategoryDTO> list(CategoryKind kind) {
        List<Category> categories = kind == null
                ? categoryRepository.findAllByOrderByKindAscNameAsc()
                : categoryRepository.findAllByKindOrderByNameAsc(kind);
        return categories.stream().map(CategoryService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public Category require(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    @Transactional
    public CategoryDTO create(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndKind(name, request.getKind())) {
            throw new ConflictException(duplicateMessage(name, request.getKind()));
        }
        Category category = categoryRepository.save(Category.builder()
                .name(name)
                .kind(request.getKind())
                .isDefault(false)
                .build());
        return toDto(category);
    }

    /** Renames only: the kind in the request is ignored, as {@link CategoryRequest} documents. */
    @Transactional
    public CategoryDTO rename(UUID id, CategoryRequest request) {
        Category category = require(id);
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCaseAndKindAndIdNot(name, category.getKind(), id)) {
            throw new ConflictException(duplicateMessage(name, category.getKind()));
        }
        category.setName(name);
        return toDto(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id) {
        Category category = require(id);
        if (category.isDefault()) {
            throw new ConflictException("'" + category.getName() + "' is a default category and cannot be deleted");
        }
        long count = transactionRepository.countByCategoryId(id);
        if (count > 0) {
            String noun = count == 1 ? "transaction" : "transactions";
            throw new ConflictException("Category '" + category.getName() + "' is used by " + count + " " + noun + " and cannot be deleted");
        }
        categoryRepository.delete(category);
    }

    private static String duplicateMessage(String name, CategoryKind kind) {
        return "An " + kind.name().toLowerCase() + " category named '" + name.toLowerCase() + "' already exists";
    }

    private static CategoryDTO toDto(Category category) {
        return CategoryDTO.builder()
                .id(category.getId())
                .name(category.getName())
                .kind(category.getKind())
                .isDefault(category.isDefault())
                .build();
    }
}
