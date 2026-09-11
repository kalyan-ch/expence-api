package com.expence.expenceApi.controller;

import com.expence.expenceApi.model.finance.rest.AccountDTO;
import com.expence.expenceApi.model.finance.rest.AccountRequest;
import com.expence.expenceApi.service.AccountService;
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
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public List<AccountDTO> list() {
        return accountService.list();
    }

    @GetMapping("/{id}")
    public AccountDTO get(@PathVariable UUID id) {
        return accountService.get(id);
    }

    @PostMapping
    public ResponseEntity<AccountDTO> create(@Valid @RequestBody AccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accountService.create(request));
    }

    @PutMapping("/{id}")
    public AccountDTO update(@PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        return accountService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        accountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
