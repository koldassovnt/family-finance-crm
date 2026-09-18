package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.BASE_CURRENCY
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.CategoryKind
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateTransactionRequest
import com.familyfinance.crm.dto.MonthlySummaryResponse
import com.familyfinance.crm.dto.ReconcileRequest
import com.familyfinance.crm.dto.UpdateTransactionRequest
import com.familyfinance.crm.dto.toSummary
import com.familyfinance.crm.exception.CurrencyMismatchException
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.exception.invalidField
import com.familyfinance.crm.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Service
class TransactionServiceImpl(
    private val transactionRepository: TransactionRepository,
    private val accountService: AccountService,
    private val categoryService: CategoryService,
    private val clock: Clock,
) : TransactionService {
    @Transactional
    override fun create(
        owner: User,
        request: CreateTransactionRequest,
    ): Transaction {
        val type = request.type ?: throw invalidField("type", "is required")
        if (type == TransactionType.ADJUSTMENT) {
            throw invalidField(
                "type",
                "cannot be created directly — use POST /api/v1/accounts/{id}/reconcile",
            )
        }
        val accountId = request.accountId ?: throw invalidField("accountId", "is required")
        val amount = requirePositiveAmount(request.amount)
        val account = accountService.getOwnedBy(accountId, owner)
        val occurredOn = resolveOccurredOn(request.occurredOn)
        val rate = resolveExchangeRate(request.exchangeRate, account)

        val transaction =
            when (type) {
                TransactionType.INCOME, TransactionType.EXPENSE -> {
                    buildSimple(
                        type = type,
                        amount = amount,
                        rate = rate,
                        account = account,
                        occurredOn = occurredOn,
                        request = request,
                        owner = owner,
                    )
                }

                TransactionType.TRANSFER -> {
                    buildTransfer(
                        amount = amount,
                        rate = rate,
                        account = account,
                        occurredOn = occurredOn,
                        request = request,
                        owner = owner,
                    )
                }

                TransactionType.ADJUSTMENT -> {
                    error("ADJUSTMENT is rejected above")
                }
            }

        val saved = transactionRepository.save(transaction)
        saved.applyToBalances(APPLY)
        return saved
    }

    @Transactional
    override fun update(
        id: UUID,
        owner: User,
        request: UpdateTransactionRequest,
    ): Transaction {
        val transaction = getOwned(id, owner)

        val newAmount =
            request.amount?.let { amount ->
                if (transaction.type == TransactionType.ADJUSTMENT) {
                    requireNonZeroAmount(amount)
                } else {
                    requirePositiveAmount(amount)
                }
            }
        val newToAmount = resolveToAmountPatch(request, transaction)
        if (newAmount != null || newToAmount != null) {
            // Both sides move in one reverse/apply pair, so a cross-currency
            // transfer never sits half-corrected. Type and accounts are
            // immutable here, so this only touches the accounts it already has.
            transaction.applyToBalances(REVERSE)
            newAmount?.let { transaction.amount = it }
            newToAmount?.let { transaction.toAmount = it }
            transaction.applyToBalances(APPLY)
        }
        request.exchangeRate?.let { newRate ->
            transaction.exchangeRate = validateExchangeRate(newRate, transaction.currency)
        }
        if (request.amount != null || request.exchangeRate != null) {
            transaction.amountKzt = toKzt(transaction.amount, transaction.exchangeRate)
        }

        request.occurredOn?.let { transaction.occurredOn = resolveOccurredOn(it) }
        request.categoryId?.let { categoryId ->
            transaction.category =
                categoryId.orElse(null)?.let { resolveCategory(it, transaction.type, owner) }
        }
        request.note?.let { transaction.note = it.orElse(null)?.let(::validateNote) }
        return transaction
    }

    @Transactional
    override fun softDelete(
        id: UUID,
        owner: User,
    ) {
        val transaction = getOwned(id, owner)
        // A hidden transaction must not leave a balance that assumes it happened.
        transaction.applyToBalances(REVERSE)
        transaction.isDeleted = true
    }

    @Transactional(readOnly = true)
    override fun history(
        accountId: UUID,
        owner: User,
        from: LocalDate,
        to: LocalDate,
    ): List<Transaction> {
        val account = accountService.getOwnedBy(accountId, owner)
        val range = historyRange(from = from, to = to)
        return transactionRepository.findHistory(account = account, from = range.from, to = range.to)
    }

    @Transactional(readOnly = true)
    override fun list(
        owner: User,
        from: LocalDate,
        to: LocalDate,
        accountId: UUID?,
        categoryId: UUID?,
    ): List<Transaction> {
        val range = historyRange(from = from, to = to)
        // Resolved only to 404 on an id that isn't the caller's; the query
        // filters on the id itself.
        accountId?.let { accountService.getOwnedBy(it, owner) }
        categoryId?.let { categoryService.getOwnedBy(it, owner) }
        return transactionRepository.findForOwner(
            owner = owner,
            from = range.from,
            to = range.to,
            accountId = accountId,
            categoryId = categoryId,
        )
    }

    @Transactional(readOnly = true)
    override fun monthlySummary(
        owner: User,
        month: YearMonth,
    ): MonthlySummaryResponse {
        val range = monthRange(month)
        // Only INCOME and EXPENSE are queried: TRANSFER just moves money between
        // the caller's own accounts, and ADJUSTMENT is a correction, not spending.
        val totalIncome = transactionRepository.sumByType(owner, TransactionType.INCOME, range.from, range.to)
        val totalExpense = transactionRepository.sumByType(owner, TransactionType.EXPENSE, range.from, range.to)
        return MonthlySummaryResponse(
            month = month.toString(),
            currency = BASE_CURRENCY,
            from = range.from,
            to = range.to,
            totalIncome = totalIncome,
            totalExpense = totalExpense,
            net = totalIncome - totalExpense,
            expenseByCategory =
                transactionRepository
                    .sumByCategory(owner, TransactionType.EXPENSE, range.from, range.to)
                    .map { it.toSummary() },
            incomeByCategory =
                transactionRepository
                    .sumByCategory(owner, TransactionType.INCOME, range.from, range.to)
                    .map { it.toSummary() },
        )
    }

    @Transactional
    override fun reconcile(
        accountId: UUID,
        owner: User,
        request: ReconcileRequest,
    ): Transaction {
        val actualBalance = request.actualBalance ?: throw invalidField("actualBalance", "is required")
        val account = accountService.getOwnedBy(accountId, owner)
        val delta = actualBalance - account.balance
        if (delta.signum() == 0) {
            throw invalidField("actualBalance", "already matches the tracked balance; nothing to correct")
        }
        val rate = resolveExchangeRate(request.exchangeRate, account)
        val saved =
            transactionRepository.save(
                Transaction(
                    type = TransactionType.ADJUSTMENT,
                    amount = delta,
                    currency = account.currency,
                    exchangeRate = rate,
                    amountKzt = toKzt(delta, rate),
                    toAmount = null,
                    occurredOn = resolveOccurredOn(request.occurredOn),
                    account = account,
                    toAccount = null,
                    // An ADJUSTMENT carries no category — it isn't spending.
                    category = null,
                    note = request.note?.let(::validateNote),
                ),
            )
        saved.applyToBalances(APPLY)
        return saved
    }

    private fun buildSimple(
        type: TransactionType,
        amount: BigDecimal,
        rate: BigDecimal,
        account: Account,
        occurredOn: LocalDate,
        request: CreateTransactionRequest,
        owner: User,
    ): Transaction {
        if (request.toAccountId != null) {
            throw invalidField("toAccountId", "is only valid for a TRANSFER")
        }
        if (request.toAmount != null) {
            throw invalidField("toAmount", "is only valid for a cross-currency TRANSFER")
        }
        return Transaction(
            type = type,
            amount = amount,
            currency = account.currency,
            exchangeRate = rate,
            amountKzt = toKzt(amount, rate),
            toAmount = null,
            occurredOn = occurredOn,
            account = account,
            toAccount = null,
            category = request.categoryId?.let { resolveCategory(it, type, owner) },
            note = request.note?.let(::validateNote),
        )
    }

    private fun buildTransfer(
        amount: BigDecimal,
        rate: BigDecimal,
        account: Account,
        occurredOn: LocalDate,
        request: CreateTransactionRequest,
        owner: User,
    ): Transaction {
        val toAccountId = request.toAccountId ?: throw invalidField("toAccountId", "is required for a TRANSFER")
        if (toAccountId == account.id) {
            throw invalidField("toAccountId", "must differ from accountId")
        }
        if (request.categoryId != null) {
            throw invalidField("categoryId", "is not valid for a TRANSFER")
        }
        val toAccount = accountService.getOwnedBy(toAccountId, owner)
        val toAmount = resolveToAmount(request.toAmount, account, toAccount)
        return Transaction(
            type = TransactionType.TRANSFER,
            amount = amount,
            currency = account.currency,
            exchangeRate = rate,
            // Describes the source movement; a transfer never reaches a total anyway.
            amountKzt = toKzt(amount, rate),
            toAmount = toAmount,
            occurredOn = occurredOn,
            account = account,
            toAccount = toAccount,
            category = null,
            note = request.note?.let(::validateNote),
        )
    }

    /**
     * `toAmount` on an edit belongs only to a cross-currency `TRANSFER`, which
     * is exactly the transaction whose two sides are credited independently.
     * Changing such a transfer's `amount` without it would correct the source
     * and leave the destination holding the old figure, so that combination is
     * rejected rather than silently half-applied.
     */
    private fun resolveToAmountPatch(
        request: UpdateTransactionRequest,
        transaction: Transaction,
    ): BigDecimal? {
        // Set at creation for exactly the cross-currency transfers, and nothing else.
        val crossCurrencyTransfer = transaction.toAmount != null
        if (request.toAmount != null && !crossCurrencyTransfer) {
            throw invalidField("toAmount", "is only valid for a cross-currency TRANSFER")
        }
        if (crossCurrencyTransfer && request.amount != null && request.toAmount == null) {
            throw invalidField(
                "toAmount",
                "is required when changing the amount of a cross-currency TRANSFER",
            )
        }
        return request.toAmount?.let { requirePositiveAmount(it, field = "toAmount") }
    }

    /**
     * Cross-currency transfers carry an explicit destination amount; same-currency
     * ones must not — a redundant or missing value is rejected rather than guessed.
     */
    private fun resolveToAmount(
        toAmount: BigDecimal?,
        account: Account,
        toAccount: Account,
    ): BigDecimal? {
        val crossCurrency = account.currency != toAccount.currency
        return when {
            crossCurrency && toAmount == null -> {
                throw CurrencyMismatchException(
                    "toAmount is required: ${account.currency} → ${toAccount.currency}",
                )
            }

            !crossCurrency && toAmount != null -> {
                throw CurrencyMismatchException(
                    "toAmount must be absent when both accounts are in ${account.currency}",
                )
            }

            crossCurrency -> {
                requirePositiveAmount(toAmount, field = "toAmount")
            }

            else -> {
                null
            }
        }
    }

    private fun resolveCategory(
        categoryId: UUID,
        type: TransactionType,
        owner: User,
    ): Category {
        if (type != TransactionType.INCOME && type != TransactionType.EXPENSE) {
            throw invalidField("categoryId", "is only valid for an INCOME or EXPENSE transaction")
        }
        val category = categoryService.getOwnedBy(categoryId, owner)
        val expectedKind =
            if (type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
        if (category.kind != expectedKind) {
            throw invalidField("categoryId", "must be a category of kind ${expectedKind.name} for a ${type.name} transaction")
        }
        return category
    }

    private fun getOwned(
        id: UUID,
        owner: User,
    ): Transaction {
        val transaction =
            transactionRepository.findDetailedById(id)
                ?: throw NotFoundException("Transaction $id was not found")
        if (transaction.account.owner != owner) throw NotFoundException("Transaction $id was not found")
        return transaction
    }

    /** "Today" is today in the app timezone; a transaction records what happened. */
    private fun resolveOccurredOn(occurredOn: LocalDate?): LocalDate {
        val today = LocalDate.now(clock)
        val resolved = occurredOn ?: today
        if (resolved.isAfter(today)) {
            throw invalidField("occurredOn", "must not be in the future")
        }
        return resolved
    }

    /**
     * A KZT account never carries a rate other than 1; anything else must say
     * what it was worth, since no rate source is stored to look it up later.
     */
    private fun resolveExchangeRate(
        exchangeRate: BigDecimal?,
        account: Account,
    ): BigDecimal =
        if (account.currency == BASE_CURRENCY) {
            validateExchangeRate(exchangeRate ?: BigDecimal.ONE, account.currency)
        } else {
            validateExchangeRate(
                exchangeRate ?: throw invalidField(
                    "exchangeRate",
                    "is required for a ${account.currency} account: give the $BASE_CURRENCY value of 1 ${account.currency}",
                ),
                account.currency,
            )
        }

    private fun validateExchangeRate(
        exchangeRate: BigDecimal,
        currency: String,
    ): BigDecimal {
        if (exchangeRate.signum() <= 0) throw invalidField("exchangeRate", "must be greater than zero")
        if (currency == BASE_CURRENCY && exchangeRate.compareTo(BigDecimal.ONE) != 0) {
            throw invalidField("exchangeRate", "must be 1 for a $BASE_CURRENCY account")
        }
        return exchangeRate
    }

    private fun validateNote(note: String): String {
        if (note.length > MAX_NOTE_LENGTH) {
            throw invalidField("note", "must be at most $MAX_NOTE_LENGTH characters")
        }
        return note
    }
}

private const val MAX_NOTE_LENGTH = 1000
private const val MONEY_SCALE = 4
private const val APPLY = 1
private const val REVERSE = -1

/**
 * Moves the balances this transaction touches. [direction] is [APPLY] to book
 * it and [REVERSE] to undo it; the two are exact inverses, which is what makes
 * edit and delete safe.
 */
private fun Transaction.applyToBalances(direction: Int) {
    val signed = amount.multiply(BigDecimal(direction))
    when (type) {
        TransactionType.INCOME -> {
            account.balance += signed
        }

        TransactionType.EXPENSE -> {
            account.balance -= signed
        }

        // The only type whose amount may be negative; the drift can go either way.
        TransactionType.ADJUSTMENT -> {
            account.balance += signed
        }

        TransactionType.TRANSFER -> {
            val destination =
                checkNotNull(toAccount) { "A TRANSFER always has a destination account" }
            account.balance -= signed
            // Cross-currency transfers credit the destination its own amount.
            destination.balance += (toAmount ?: amount).multiply(BigDecimal(direction))
        }
    }
}

private fun toKzt(
    amount: BigDecimal,
    exchangeRate: BigDecimal,
): BigDecimal = amount.multiply(exchangeRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

private fun requirePositiveAmount(
    amount: BigDecimal?,
    field: String = "amount",
): BigDecimal {
    val value = amount ?: throw invalidField(field, "is required")
    if (value.signum() <= 0) throw invalidField(field, "must be greater than zero")
    return value
}

private fun requireNonZeroAmount(amount: BigDecimal): BigDecimal {
    if (amount.signum() == 0) throw invalidField("amount", "must not be zero")
    return amount
}
