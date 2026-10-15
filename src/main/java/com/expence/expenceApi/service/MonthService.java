package com.expence.expenceApi.service;

import com.expence.expenceApi.dao.MonthBalanceRepository;
import com.expence.expenceApi.dao.MonthItemRepository;
import com.expence.expenceApi.dao.PlanMonthRepository;
import com.expence.expenceApi.dao.RecurringItemRepository;
import com.expence.expenceApi.exception.NotFoundException;
import com.expence.expenceApi.model.finance.Account;
import com.expence.expenceApi.model.finance.ItemKind;
import com.expence.expenceApi.model.finance.ItemStatus;
import com.expence.expenceApi.model.finance.MonthBalance;
import com.expence.expenceApi.model.finance.MonthItem;
import com.expence.expenceApi.model.finance.PlanMonth;
import com.expence.expenceApi.model.finance.RecurringItem;
import com.expence.expenceApi.model.finance.rest.MonthBalanceRequest;
import com.expence.expenceApi.model.finance.rest.MonthDTO;
import com.expence.expenceApi.model.finance.rest.MonthItemRequest;
import com.expence.expenceApi.model.finance.rest.MonthSummaryDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MonthService {

    private static final Set<ItemKind> OUTGOING = Set.of(ItemKind.EXPENSE, ItemKind.CARD_PAYMENT, ItemKind.SAVINGS);
    private static final Set<ItemKind> SPENDING = Set.of(ItemKind.EXPENSE, ItemKind.CARD_PAYMENT);

    private static final Comparator<MonthItem> BY_BILLING_DAY = Comparator
            .comparing(MonthItem::getBillingDay, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MonthItem::getSortOrder);

    private final PlanMonthRepository planMonthRepository;
    private final MonthItemRepository monthItemRepository;
    private final MonthBalanceRepository monthBalanceRepository;
    private final RecurringItemRepository recurringItemRepository;
    private final AccountService accountService;

    /** Opening a month that doesn't exist yet starts it from the active recurring items. */
    @Transactional
    public MonthDTO get(YearMonth month) {
        return view(plan(month));
    }

    /** Adds a one-off row: not tied to a recurring item, so it never repeats. */
    @Transactional
    public MonthDTO addItem(YearMonth month, MonthItemRequest request) {
        PlanMonth plan = plan(month);
        MonthItem item = MonthItem.builder().planMonth(plan).build();
        apply(item, request);
        monthItemRepository.save(item);
        return view(plan);
    }

    @Transactional
    public MonthDTO updateItem(YearMonth month, UUID id, MonthItemRequest request) {
        MonthItem item = requireItem(month, id);
        apply(item, request);
        return view(item.getPlanMonth());
    }

    @Transactional
    public MonthDTO deleteItem(YearMonth month, UUID id) {
        MonthItem item = requireItem(month, id);
        monthItemRepository.delete(item);
        monthItemRepository.flush();
        return view(item.getPlanMonth());
    }

    @Transactional
    public MonthDTO setBalance(YearMonth month, UUID accountId, MonthBalanceRequest request) {
        PlanMonth plan = plan(month);
        MonthBalance balance = monthBalanceRepository.findByPlanMonthIdAndAccountId(plan.getId(), accountId)
                .orElseGet(() -> MonthBalance.builder().planMonth(plan).account(accountService.require(accountId)).build());
        balance.setStartingBalance(request.getStartingBalance());
        monthBalanceRepository.save(balance);
        return view(plan);
    }

    private PlanMonth plan(YearMonth month) {
        return planMonthRepository.findByMonth(month.atDay(1)).orElseGet(() -> start(month));
    }

    private MonthItem requireItem(YearMonth month, UUID id) {
        return monthItemRepository.findById(id)
                .filter(i -> i.getPlanMonth().getMonth().equals(month.atDay(1)))
                .orElseThrow(() -> new NotFoundException("Bill not found"));
    }

    /** The DB rejects a paid date on a pending row, so status drives it. */
    private void apply(MonthItem item, MonthItemRequest request) {
        boolean paid = request.getStatus() == ItemStatus.PAID;
        item.setName(request.getName().trim());
        item.setKind(request.getKind());
        item.setAccount(accountService.requirePayment(request.getAccountId(), item.getAccount()));
        item.setAmount(request.getAmount());
        item.setBillingDay(request.getBillingDay());
        item.setStatus(paid ? ItemStatus.PAID : ItemStatus.PENDING);
        item.setPaidOn(!paid ? null : request.getPaidOn() != null ? request.getPaidOn() : LocalDate.now());
    }

    private MonthDTO view(PlanMonth plan) {
        YearMonth month = YearMonth.from(plan.getMonth());
        List<MonthItem> items = monthItemRepository.findAllByPlanMonthId(plan.getId());
        List<MonthBalance> balances = monthBalanceRepository.findAllByPlanMonthId(plan.getId());
        return MonthDTO.builder()
                .month(month)
                .items(items.stream().sorted(BY_BILLING_DAY).map(MonthService::toDto).toList())
                .accounts(accountTotals(items, balances))
                .total(sum(items, i -> OUTGOING.contains(i.getKind())))
                .expenditure(sum(items, i -> SPENDING.contains(i.getKind())))
                .unpaidCount((int) items.stream().filter(MonthService::isDue).count())
                .unpaidTotal(sum(items, MonthService::isDue))
                .build();
    }

    // ponytail: one items query per month; fine for a few dozen months. Aggregate in SQL if the list grows slow.
    @Transactional(readOnly = true)
    public List<MonthSummaryDTO> list() {
        return planMonthRepository.findAllByOrderByMonthAsc().stream().map(plan -> {
            List<MonthItem> items = monthItemRepository.findAllByPlanMonthId(plan.getId());
            return MonthSummaryDTO.builder()
                    .month(YearMonth.from(plan.getMonth()))
                    .total(sum(items, i -> OUTGOING.contains(i.getKind())))
                    .expenditure(sum(items, i -> SPENDING.contains(i.getKind())))
                    .build();
        }).toList();
    }

    private PlanMonth start(YearMonth month) {
        PlanMonth plan = planMonthRepository.save(PlanMonth.builder().month(month.atDay(1)).build());
        monthItemRepository.saveAll(recurringItemRepository.findAllByOrderBySortOrderAscNameAsc().stream()
                .filter(RecurringItem::isActive)
                .map(r -> MonthItem.builder()
                        .planMonth(plan)
                        .recurringItem(r)
                        .name(r.getName())
                        .kind(r.getKind())
                        .account(r.getAccount())
                        .amount(r.getDefaultAmount())
                        .billingDay(r.getBillingDay())
                        .sortOrder(r.getSortOrder())
                        .build())
                .toList());
        return plan;
    }

    /** One row per account that has a balance or an item this month, sorted by name. */
    private static List<MonthDTO.AccountTotals> accountTotals(List<MonthItem> items, List<MonthBalance> balances) {
        Map<String, Account> accounts = new TreeMap<>();
        Stream.concat(items.stream().map(MonthItem::getAccount), balances.stream().map(MonthBalance::getAccount))
                .forEach(a -> accounts.put(a.getName(), a));
        return accounts.values().stream().map(account -> {
            BigDecimal start = balances.stream()
                    .filter(b -> b.getAccount().getId().equals(account.getId()))
                    .map(MonthBalance::getStartingBalance)
                    .findFirst().orElse(null);
            List<MonthItem> own = items.stream().filter(i -> i.getAccount().getId().equals(account.getId())).toList();
            BigDecimal income = sum(own, i -> i.getKind() == ItemKind.INCOME);
            BigDecimal expenses = sum(own, i -> OUTGOING.contains(i.getKind()));
            return MonthDTO.AccountTotals.builder()
                    .accountId(account.getId())
                    .accountName(account.getName())
                    .startingBalance(start)
                    .income(income)
                    .expenses(expenses)
                    .remaining((start == null ? BigDecimal.ZERO : start).add(income).subtract(expenses))
                    .build();
        }).toList();
    }

    private static boolean isDue(MonthItem item) {
        return OUTGOING.contains(item.getKind()) && item.getAmount() != null && item.getStatus() == ItemStatus.PENDING;
    }

    /** Blank amounts count as zero. */
    private static BigDecimal sum(Collection<MonthItem> items, Predicate<MonthItem> filter) {
        return items.stream().filter(filter)
                .map(i -> i.getAmount() == null ? BigDecimal.ZERO : i.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static MonthDTO.Item toDto(MonthItem item) {
        return MonthDTO.Item.builder()
                .id(item.getId())
                .recurringItemId(item.getRecurringItem() == null ? null : item.getRecurringItem().getId())
                .name(item.getName())
                .kind(item.getKind())
                .accountId(item.getAccount().getId())
                .accountName(item.getAccount().getName())
                .amount(item.getAmount())
                .billingDay(item.getBillingDay())
                .sortOrder(item.getSortOrder())
                .status(item.getStatus())
                .paidOn(item.getPaidOn())
                .build();
    }
}
