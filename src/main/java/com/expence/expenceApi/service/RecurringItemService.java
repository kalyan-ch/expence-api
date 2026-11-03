package com.expence.expenceApi.service;

import com.expence.expenceApi.dao.MonthItemRepository;
import com.expence.expenceApi.dao.RecurringItemRepository;
import com.expence.expenceApi.exception.ConflictException;
import com.expence.expenceApi.exception.NotFoundException;
import com.expence.expenceApi.model.finance.RecurringItem;
import com.expence.expenceApi.model.finance.rest.RecurringItemDTO;
import com.expence.expenceApi.model.finance.rest.RecurringItemRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecurringItemService {

    private final RecurringItemRepository recurringItemRepository;
    private final MonthItemRepository monthItemRepository;
    private final AccountService accountService;
    private final MonthService monthService;

    @Transactional(readOnly = true)
    public List<RecurringItemDTO> list() {
        return recurringItemRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .map(RecurringItemService::toDto)
                .toList();
    }

    @Transactional
    public RecurringItemDTO create(RecurringItemRequest request) {
        String name = request.getName().trim();
        if (recurringItemRepository.existsByNameIgnoreCase(name)) {
            throw duplicate(name);
        }
        RecurringItem item = RecurringItem.builder().name(name).build();
        apply(item, request);
        RecurringItem saved = recurringItemRepository.save(item);
        monthService.addToOpenMonths(saved);
        return toDto(saved);
    }

    @Transactional
    public RecurringItemDTO update(UUID id, RecurringItemRequest request) {
        RecurringItem item = require(id);
        String name = request.getName().trim();
        if (recurringItemRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicate(name);
        }
        item.setName(name);
        apply(item, request);
        return toDto(recurringItemRepository.save(item));
    }

    @Transactional
    public void delete(UUID id) {
        RecurringItem item = require(id);
        long count = monthItemRepository.countByRecurringItemId(id);
        if (count > 0) {
            String noun = count == 1 ? "month" : "months";
            throw new ConflictException("'" + item.getName() + "' is used by " + count + " " + noun + "; retire it instead");
        }
        recurringItemRepository.delete(item);
    }

    private RecurringItem require(UUID id) {
        return recurringItemRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Bill not found"));
    }

    private void apply(RecurringItem item, RecurringItemRequest request) {
        item.setKind(request.getKind());
        item.setAccount(accountService.requirePayment(request.getAccountId(), item.getAccount()));
        item.setDefaultAmount(request.getDefaultAmount());
        item.setBillingDay(request.getBillingDay());
        item.setInExpenditure(item.getKind().inExpenditure(request.getInExpenditure(), item.getId() == null ? null : item.isInExpenditure()));
        applySchedule(item, request);
        if (request.getSortOrder() != null) item.setSortOrder(request.getSortOrder());
        if (request.getActive() != null) item.setActive(request.getActive());
    }

    /** Keeps only the fields the frequency uses, so the stored row always matches one schedule shape. */
    private static void applySchedule(RecurringItem item, RecurringItemRequest request) {
        if (request.getFrequency() != null) item.setFrequency(request.getFrequency());
        item.setSecondDay(null);
        item.setAnchorDate(null);
        switch (item.getFrequency()) {
            case MONTHLY -> { }
            case TWICE_MONTHLY -> {
                if (request.getBillingDay() == null || request.getSecondDay() == null
                        || request.getBillingDay().equals(request.getSecondDay())) {
                    throw new IllegalArgumentException("Twice a month needs two different days");
                }
                item.setSecondDay(request.getSecondDay());
            }
            case BIWEEKLY, WEEKLY -> {
                if (request.getAnchorDate() == null) {
                    throw new IllegalArgumentException("Pick a recent payday to count the weeks from");
                }
                item.setBillingDay(null);
                item.setAnchorDate(request.getAnchorDate());
            }
        }
    }

    private static ConflictException duplicate(String name) {
        return new ConflictException("A bill named '" + name.toLowerCase() + "' already exists");
    }

    private static RecurringItemDTO toDto(RecurringItem item) {
        return RecurringItemDTO.builder()
                .id(item.getId())
                .name(item.getName())
                .kind(item.getKind())
                .accountId(item.getAccount().getId())
                .accountName(item.getAccount().getName())
                .defaultAmount(item.getDefaultAmount())
                .billingDay(item.getBillingDay())
                .inExpenditure(item.isInExpenditure())
                .frequency(item.getFrequency())
                .secondDay(item.getSecondDay())
                .anchorDate(item.getAnchorDate())
                .sortOrder(item.getSortOrder())
                .active(item.isActive())
                .build();
    }
}
