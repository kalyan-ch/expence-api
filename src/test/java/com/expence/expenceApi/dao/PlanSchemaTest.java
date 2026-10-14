package com.expence.expenceApi.dao;

import com.expence.expenceApi.AbstractIntegrationTest;
import com.expence.expenceApi.model.finance.Account;
import com.expence.expenceApi.model.finance.AccountType;
import com.expence.expenceApi.model.finance.ItemKind;
import com.expence.expenceApi.model.finance.ItemStatus;
import com.expence.expenceApi.model.finance.MonthBalance;
import com.expence.expenceApi.model.finance.MonthItem;
import com.expence.expenceApi.model.finance.PlanMonth;
import com.expence.expenceApi.model.finance.RecurringItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the monthly-plan migration and its entities agree, and that the database refuses
 * plan rows that would break the sheet's arithmetic. Rolled back after each test so plan
 * rows never block the other suites from deleting accounts.
 */
@Transactional
class PlanSchemaTest extends AbstractIntegrationTest {

	@Autowired private AccountRepository accountRepository;
	@Autowired private RecurringItemRepository recurringItemRepository;
	@Autowired private PlanMonthRepository planMonthRepository;
	@Autowired private MonthBalanceRepository monthBalanceRepository;
	@Autowired private MonthItemRepository monthItemRepository;

	private Account chase;
	private PlanMonth october;

	@BeforeEach
	void seed() {
		chase = accountRepository.save(Account.builder()
				.name("Chase " + UUID.randomUUID()).type(AccountType.CHECKING)
				.openingBalance(BigDecimal.ZERO).build());
		october = planMonthRepository.save(PlanMonth.builder().month(LocalDate.of(2026, 10, 1)).build());
	}

	private RecurringItem rent() {
		return recurringItemRepository.save(RecurringItem.builder()
				.name("Rent " + UUID.randomUUID()).kind(ItemKind.EXPENSE).account(chase)
				.defaultAmount(new BigDecimal("2010.82")).billingDay(1).build());
	}

	private MonthItem.MonthItemBuilder item() {
		return MonthItem.builder().planMonth(october).name("Rent").kind(ItemKind.EXPENSE)
				.account(chase).amount(new BigDecimal("2010.82")).billingDay(1);
	}

	@Test
	@DisplayName("round-trips a recurring item and its month copy with database defaults")
	void roundTrips() {
		RecurringItem rent = rent();
		MonthItem saved = monthItemRepository.saveAndFlush(item().recurringItem(rent).build());

		assertThat(rent.isActive()).isTrue();
		assertThat(saved.getStatus()).isEqualTo(ItemStatus.PENDING);
		assertThat(monthItemRepository.findById(saved.getId())).get()
				.extracting(MonthItem::getRecurringItem).isEqualTo(rent);
	}

	@Test
	@DisplayName("a month is stored as its first day")
	void monthIsFirstDay() {
		assertThatThrownBy(() -> planMonthRepository.saveAndFlush(
				PlanMonth.builder().month(LocalDate.of(2026, 11, 15)).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("one starting balance per account per month")
	void oneBalancePerAccountPerMonth() {
		monthBalanceRepository.saveAndFlush(MonthBalance.builder()
				.planMonth(october).account(chase).startingBalance(new BigDecimal("2915.91")).build());

		assertThatThrownBy(() -> monthBalanceRepository.saveAndFlush(MonthBalance.builder()
				.planMonth(october).account(chase).startingBalance(BigDecimal.ONE).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("a recurring item is copied into a month at most once")
	void recurringItemOncePerMonth() {
		RecurringItem rent = rent();
		monthItemRepository.saveAndFlush(item().recurringItem(rent).build());

		assertThatThrownBy(() -> monthItemRepository.saveAndFlush(item().recurringItem(rent).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("billing day must be 1 to 31")
	void billingDayInRange() {
		assertThatThrownBy(() -> monthItemRepository.saveAndFlush(item().billingDay(32).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("amount is a positive magnitude; blank means nothing due")
	void amountPositiveOrBlank() {
		monthItemRepository.saveAndFlush(item().name("India").amount(null).build());

		assertThatThrownBy(() -> monthItemRepository.saveAndFlush(item().amount(new BigDecimal("-5")).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("only a paid item carries a paid date")
	void paidDateOnlyWhenPaid() {
		assertThatThrownBy(() -> monthItemRepository.saveAndFlush(item().paidOn(LocalDate.of(2026, 10, 1)).build()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
