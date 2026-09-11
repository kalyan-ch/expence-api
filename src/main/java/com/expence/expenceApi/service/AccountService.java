package com.expence.expenceApi.service;

import com.expence.expenceApi.dao.AccountBalanceRow;
import com.expence.expenceApi.dao.AccountRepository;
import com.expence.expenceApi.dao.TransactionRepository;
import com.expence.expenceApi.exception.ConflictException;
import com.expence.expenceApi.exception.NotFoundException;
import com.expence.expenceApi.model.finance.Account;
import com.expence.expenceApi.model.finance.rest.AccountDTO;
import com.expence.expenceApi.model.finance.rest.AccountRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public List<AccountDTO> list() {
        Map<UUID, BigDecimal> totals = transactionRepository.sumGroupedByAccount().stream()
                .collect(Collectors.toMap(AccountBalanceRow::getAccountId, AccountBalanceRow::getTotal));
        return accountRepository.findAll().stream()
                .map(a -> toDto(a, totals.getOrDefault(a.getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(AccountDTO::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountDTO get(UUID id) {
        Account account = require(id);
        BigDecimal sum = transactionRepository.sumByAccountId(id);
        return toDto(account, sum);
    }

    @Transactional(readOnly = true)
    public Account require(UUID id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    @Transactional(readOnly = true)
    public BigDecimal totalBalance() {
        return list().stream()
                .map(AccountDTO::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public AccountDTO create(AccountRequest request) {
        String name = request.getName().trim();
        if (accountRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("An account named '" + name.toLowerCase() + "' already exists");
        }
        Account account = Account.builder()
                .name(name)
                .type(request.getType())
                .openingBalance(request.getOpeningBalance())
                .build();
        account = accountRepository.save(account);
        return toDto(account, BigDecimal.ZERO);
    }

    @Transactional
    public AccountDTO update(UUID id, AccountRequest request) {
        Account account = require(id);
        String name = request.getName().trim();
        if (accountRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new ConflictException("An account named '" + name.toLowerCase() + "' already exists");
        }
        account.setName(name);
        account.setType(request.getType());
        account.setOpeningBalance(request.getOpeningBalance());
        account = accountRepository.save(account);
        BigDecimal sum = transactionRepository.sumByAccountId(id);
        return toDto(account, sum);
    }

    @Transactional
    public void delete(UUID id) {
        Account account = require(id);
        long count = transactionRepository.countByAccountId(id);
        if (count > 0) {
            String noun = count == 1 ? "transaction" : "transactions";
            throw new ConflictException("Account '" + account.getName() + "' has " + count + " " + noun + " and cannot be deleted");
        }
        accountRepository.delete(account);
    }

    private static AccountDTO toDto(Account account, BigDecimal sum) {
        BigDecimal opening = account.getOpeningBalance() != null ? account.getOpeningBalance() : BigDecimal.ZERO;
        return AccountDTO.builder()
                .id(account.getId())
                .name(account.getName())
                .type(account.getType())
                .openingBalance(opening)
                .balance(opening.add(sum))
                .build();
    }
}
