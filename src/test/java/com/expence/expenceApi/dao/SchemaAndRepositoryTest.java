package com.expence.expenceApi.dao;

import com.expence.expenceApi.AbstractIntegrationTest;
import com.expence.expenceApi.model.finance.Account;
import com.expence.expenceApi.model.finance.AccountType;
import com.expence.expenceApi.model.finance.Category;
import com.expence.expenceApi.model.finance.CategoryKind;
import com.expence.expenceApi.model.finance.Transaction;
import com.expence.expenceApi.model.finance.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the Flyway schema and the JPA layer agree, and that the database itself
 * refuses a ledger row that would break an invariant. Every constraint case uses
 * {@code saveAndFlush} so the INSERT is sent inside the test rather than at an
 * unpredictable flush point.
 */
class SchemaAndRepositoryTest extends AbstractIntegrationTest {

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private TransactionRepository transactionRepository;

	private Account checking;

	@BeforeEach
	void resetLedger() {
		transactionRepository.deleteAll();
		accountRepository.deleteAll();
		checking = accountRepository.save(Account.builder()
				.name("Checking " + UUID.randomUUID())
				.type(AccountType.CHECKING)
				.openingBalance(new BigDecimal("100.0000"))
				.build());
	}

	private Category expenseCategory() {
		return categoryRepository.findAllByKindOrderByNameAsc(CategoryKind.EXPENSE).getFirst();
	}

	@Test
	@DisplayName("seeds three income and nine expense default categories")
	void seedsTwelveDefaultCategories() {
		List<Category> income = categoryRepository.findAllByKindOrderByNameAsc(CategoryKind.INCOME);
		List<Category> expense = categoryRepository.findAllByKindOrderByNameAsc(CategoryKind.EXPENSE);

		assertThat(income).hasSize(3).allMatch(Category::isDefault);
		assertThat(expense).hasSize(9).allMatch(Category::isDefault);
		assertThat(income).extracting(Category::getName)
				.containsExactly("Interest", "Other Income", "Salary");
		assertThat(categoryRepository.findAllByOrderByKindAscNameAsc()).hasSize(12);

		// A name is unique only within its kind, and matching ignores case.
		Category other = expense.stream().filter(c -> c.getName().equals("Other")).findFirst().orElseThrow();
		assertThat(categoryRepository.existsByNameIgnoreCaseAndKind("other", CategoryKind.EXPENSE)).isTrue();
		assertThat(categoryRepository.existsByNameIgnoreCaseAndKind("other", CategoryKind.INCOME)).isFalse();
		assertThat(categoryRepository.existsByNameIgnoreCaseAndKindAndIdNot(
				"Other", CategoryKind.EXPENSE, other.getId())).isFalse();
	}

	@Test
	@DisplayName("sums signed transaction amounts per account")
	void sumsTransactionsPerAccount() {
		transactionRepository.save(Transaction.builder()
				.account(checking)
				.category(categoryRepository.findAllByKindOrderByNameAsc(CategoryKind.INCOME).getFirst())
				.amount(new BigDecimal("250.0000"))
				.type(TransactionType.INCOME)
				.occurredOn(LocalDate.of(2026, 9, 1))
				.description("Payday")
				.build());
		transactionRepository.save(Transaction.builder()
				.account(checking)
				.category(expenseCategory())
				.amount(new BigDecimal("-40.5000"))
				.type(TransactionType.EXPENSE)
				.occurredOn(LocalDate.of(2026, 9, 2))
				.description("Groceries")
				.build());

		assertThat(transactionRepository.sumByAccountId(checking.getId()))
				.isEqualByComparingTo("209.5000");
		assertThat(transactionRepository.countByAccountId(checking.getId())).isEqualTo(2);
		assertThat(transactionRepository.countByCategoryId(expenseCategory().getId())).isEqualTo(1);

		List<AccountBalanceRow> grouped = transactionRepository.sumGroupedByAccount();
		assertThat(grouped).singleElement().satisfies(row -> {
			assertThat(row.getAccountId()).isEqualTo(checking.getId());
			assertThat(row.getTotal()).isEqualByComparingTo("209.5000");
		});
	}

	@Test
	@DisplayName("sums to zero for an account with no transactions")
	void sumIsZeroForUntouchedAccount() {
		assertThat(transactionRepository.sumByAccountId(checking.getId()))
				.isEqualByComparingTo("0");
	}

	@Test
	@DisplayName("rejects an EXPENSE with a positive amount")
	void rejectsPositiveExpense() {
		Transaction wrongSign = Transaction.builder()
				.account(checking)
				.category(expenseCategory())
				.amount(new BigDecimal("40.0000"))
				.type(TransactionType.EXPENSE)
				.occurredOn(LocalDate.of(2026, 9, 3))
				.build();

		assertThatThrownBy(() -> transactionRepository.saveAndFlush(wrongSign))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("rejects a TRANSFER leg with no transfer group id")
	void rejectsTransferWithoutGroupId() {
		Transaction orphanLeg = Transaction.builder()
				.account(checking)
				.amount(new BigDecimal("-25.0000"))
				.type(TransactionType.TRANSFER)
				.occurredOn(LocalDate.of(2026, 9, 4))
				.build();

		assertThatThrownBy(() -> transactionRepository.saveAndFlush(orphanLeg))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("rejects a zero amount")
	void rejectsZeroAmount() {
		Transaction zero = Transaction.builder()
				.account(checking)
				.category(expenseCategory())
				.amount(BigDecimal.ZERO)
				.type(TransactionType.EXPENSE)
				.occurredOn(LocalDate.of(2026, 9, 5))
				.build();

		assertThatThrownBy(() -> transactionRepository.saveAndFlush(zero))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("finds both legs of a transfer by its group id")
	void findsBothTransferLegsByGroupId() {
		Account savings = accountRepository.save(Account.builder()
				.name("Savings " + UUID.randomUUID())
				.type(AccountType.SAVINGS)
				.openingBalance(BigDecimal.ZERO)
				.build());
		UUID groupId = UUID.randomUUID();

		transactionRepository.save(Transaction.builder()
				.account(checking)
				.amount(new BigDecimal("-75.0000"))
				.type(TransactionType.TRANSFER)
				.occurredOn(LocalDate.of(2026, 9, 6))
				.transferGroupId(groupId)
				.build());
		transactionRepository.save(Transaction.builder()
				.account(savings)
				.amount(new BigDecimal("75.0000"))
				.type(TransactionType.TRANSFER)
				.occurredOn(LocalDate.of(2026, 9, 6))
				.transferGroupId(groupId)
				.build());

		List<Transaction> legs = transactionRepository.findAllByTransferGroupId(groupId);

		assertThat(legs).hasSize(2);
		assertThat(legs).extracting(Transaction::getAmount)
				.usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
				.containsExactlyInAnyOrder(new BigDecimal("-75.0000"), new BigDecimal("75.0000"));
		assertThat(legs).allMatch(leg -> leg.getCategory() == null);
	}

	@Test
	@DisplayName("detects a duplicate row so CSV import can skip it")
	void detectsDuplicateRowForImport() {
		LocalDate occurredOn = LocalDate.of(2026, 9, 7);
		BigDecimal amount = new BigDecimal("-12.3400");

		assertThat(transactionRepository.existsByAccountIdAndOccurredOnAndAmountAndDescription(
				checking.getId(), occurredOn, amount, "Coffee")).isFalse();

		transactionRepository.save(Transaction.builder()
				.account(checking)
				.category(expenseCategory())
				.amount(amount)
				.type(TransactionType.EXPENSE)
				.occurredOn(occurredOn)
				.description("Coffee")
				.build());

		assertThat(transactionRepository.existsByAccountIdAndOccurredOnAndAmountAndDescription(
				checking.getId(), occurredOn, amount, "Coffee")).isTrue();
	}

	@Test
	@DisplayName("detects a duplicate account name case-insensitively")
	void detectsDuplicateAccountNameIgnoringCase() {
		Account other = accountRepository.save(Account.builder()
				.name("Everyday Wallet")
				.type(AccountType.CASH)
				.openingBalance(BigDecimal.ZERO)
				.build());

		assertThat(accountRepository.existsByNameIgnoreCase("everyday wallet")).isTrue();
		assertThat(accountRepository.existsByNameIgnoreCase("Nothing Here")).isFalse();
		// Renaming an account to its own name is not a clash; renaming it to another's is.
		assertThat(accountRepository.existsByNameIgnoreCaseAndIdNot("EVERYDAY WALLET", other.getId()))
				.isFalse();
		assertThat(accountRepository.existsByNameIgnoreCaseAndIdNot("everyday wallet", checking.getId()))
				.isTrue();
	}

}
