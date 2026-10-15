package com.expence.expenceApi.controller;

import com.expence.expenceApi.model.finance.rest.RecurringItemDTO;
import com.expence.expenceApi.model.finance.rest.RecurringItemRequest;
import com.expence.expenceApi.service.RecurringItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recurring-items")
@RequiredArgsConstructor
public class RecurringItemController {

    private final RecurringItemService recurringItemService;

    @GetMapping
    public List<RecurringItemDTO> list() {
        return recurringItemService.list();
    }

    @PostMapping
    public ResponseEntity<RecurringItemDTO> create(@Valid @RequestBody RecurringItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(recurringItemService.create(request));
    }

    @PutMapping("/{id}")
    public RecurringItemDTO update(@PathVariable UUID id, @Valid @RequestBody RecurringItemRequest request) {
        return recurringItemService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        recurringItemService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
