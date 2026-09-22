package com.expence.expenceApi.service;

import com.expence.expenceApi.dao.TransactionRepository;
import com.expence.expenceApi.dao.spec.TransactionSpecifications;
import com.expence.expenceApi.exception.ConflictException;
import com.expence.expenceApi.exception.NotFoundException;
import com.expence.expenceApi.model.finance.Category;
import com.expence.expenceApi.model.finance.CategoryKind;
import com.expence.expenceApi.model.finance.Transaction;
import com.expence.expenceApi.model.finance.TransactionType;
import com.expence.expenceApi.model.finance.rest.PageDTO;
import com.expence.expenceApi.model.finance.rest.TransactionDTO;
import com.expence.expenceApi.model.finance.rest.TransactionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionService {

    /** Newest first, with createdAt breaking ties between rows on the same day. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "occurredOn", "createdAt");

    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;

    @Transactional(readOnly = true)
    public PageDTO<TransactionDTO> list(LocalDate from, LocalDate to, UUID accountId, UUID categoryId,
                                        TransactionType type, int page, int size) {
        var spec = TransactionSpecifications.filter(from, to, accountId, categoryId, type);
        var result = transactionRepository.findAll(spec, PageRequest.of(page, size, NEWEST_FIRST));
        return PageDTO.of(result, TransactionService::toDto);
    }

    @Transactional(readOnly = true)
    public List<TransactionDTO> recent(int limit) {
        return transactionRepository.findAll(PageRequest.of(0, limit, NEWEST_FIRST))
                .map(TransactionService::toDto)
                .getContent();
    }

    @Transactional(readOnly = true)
    public Transaction require(UUID id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    @Transactional
    public TransactionDTO create(TransactionRequest request) {
        BigDecimal amount = signed(request);
        Transaction transaction = Transaction.builder()
                .account(accountService.require(request.getAccountId()))
                .category(resolveCategory(request))
                .amount(amount)
                .type(request.getType())
                .occurredOn(request.getOccurredOn())
                .description(trimToNull(request.getDescription()))
                .build();
        return toDto(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionDTO update(UUID id, TransactionRequest request) {
        BigDecimal amount = signed(request);
        Transaction transaction = requireEditable(id);
        transaction.setAccount(accountService.require(request.getAccountId()));
        transaction.setCategory(resolveCategory(request));
        transaction.setAmount(amount);
        transaction.setType(request.getType());
        transaction.setOccurredOn(request.getOccurredOn());
        transaction.setDescription(trimToNull(request.getDescription()));
        return toDto(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(UUID id) {
        transactionRepository.delete(requireEditable(id));
    }

    /**
     * The one place the ledger's sign convention lives: the request carries a positive
     * magnitude and EXPENSE is stored negative.
     */
    private static BigDecimal signed(TransactionRequest request) {
        if (request.getType() == TransactionType.TRANSFER) {
            // IllegalArgumentException, not ConflictException: the handler maps it to 400.
            throw new IllegalArgumentException("Use POST /api/transfers to move money between accounts");
        }
        return request.getType() == TransactionType.EXPENSE
                ? request.getAmount().negate()
                : request.getAmount();
    }

    private Category resolveCategory(TransactionRequest request) {
        if (request.getCategoryId() == null) {
            return null;
        }
        Category category = categoryService.require(request.getCategoryId());
        CategoryKind expected = request.getType() == TransactionType.INCOME
                ? CategoryKind.INCOME
                : CategoryKind.EXPENSE;
        if (category.getKind() != expected) {
            // "an" is always right here: both INCOME and EXPENSE start with a vowel.
            throw new ConflictException("Category '" + category.getName() + "' is an "
                    + category.getKind().name().toLowerCase() + " category and cannot be used on an "
                    + request.getType().name().toLowerCase() + " transaction");
        }
        return category;
    }

    /** A transfer leg is only ever edited or deleted through the transfer that owns it. */
    private Transaction requireEditable(UUID id) {
        Transaction transaction = require(id);
        if (transaction.getTransferGroupId() != null) {
            throw new ConflictException("This transaction is part of a transfer; edit or delete the transfer instead");
        }
        return transaction;
    }

    private static String trimToNull(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static TransactionDTO toDto(Transaction transaction) {
        Category category = transaction.getCategory();
        return TransactionDTO.builder()
                .id(transaction.getId())
                .accountId(transaction.getAccount().getId())
                .accountName(transaction.getAccount().getName())
                .categoryId(category != null ? category.getId() : null)
                .categoryName(category != null ? category.getName() : null)
                .amount(transaction.getAmount())
                .type(transaction.getType())
                .occurredOn(transaction.getOccurredOn())
                .description(transaction.getDescription())
                .transferGroupId(transaction.getTransferGroupId())
                .build();
    }
}
