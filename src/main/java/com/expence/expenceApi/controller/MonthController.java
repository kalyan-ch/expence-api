package com.expence.expenceApi.controller;

import com.expence.expenceApi.model.finance.rest.MonthBalanceRequest;
import com.expence.expenceApi.model.finance.rest.MonthDTO;
import com.expence.expenceApi.model.finance.rest.MonthItemRequest;
import com.expence.expenceApi.model.finance.rest.MonthSummaryDTO;
import com.expence.expenceApi.service.MonthService;
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

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/** Every change returns the whole month, because one edit moves the totals and the account panel. */
@RestController
@RequestMapping("/api/months")
@RequiredArgsConstructor
public class MonthController {

    private final MonthService monthService;

    @GetMapping
    public List<MonthSummaryDTO> list() {
        return monthService.list();
    }

    /** {@code month} is {@code yyyy-MM}. A month not planned yet is started from the recurring items. */
    @GetMapping("/{month}")
    public MonthDTO get(@PathVariable YearMonth month) {
        return monthService.get(month);
    }

    @PostMapping("/{month}/items")
    public ResponseEntity<MonthDTO> addItem(@PathVariable YearMonth month, @Valid @RequestBody MonthItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(monthService.addItem(month, request));
    }

    @PutMapping("/{month}/items/{id}")
    public MonthDTO updateItem(@PathVariable YearMonth month, @PathVariable UUID id,
                               @Valid @RequestBody MonthItemRequest request) {
        return monthService.updateItem(month, id, request);
    }

    @DeleteMapping("/{month}/items/{id}")
    public MonthDTO deleteItem(@PathVariable YearMonth month, @PathVariable UUID id) {
        return monthService.deleteItem(month, id);
    }

    @PutMapping("/{month}/balances/{accountId}")
    public MonthDTO setBalance(@PathVariable YearMonth month, @PathVariable UUID accountId,
                               @Valid @RequestBody MonthBalanceRequest request) {
        return monthService.setBalance(month, accountId, request);
    }
}
