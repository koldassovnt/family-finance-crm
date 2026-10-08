package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Account
import com.familyfinance.crm.domain.BASE_CURRENCY
import com.familyfinance.crm.domain.Category
import com.familyfinance.crm.domain.CategoryKind
import com.familyfinance.crm.domain.TradeSide
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.TransactionType
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.CreateTransactionRequest
import com.familyfinance.crm.dto.MonthlySummaryResponse
import com.familyfinance.crm.dto.ReconcileRequest
import com.familyfinance.crm.dto.UpdateTransactionRequest
import com.familyfinance.crm.dto.toSummary
import com.familyfinance.crm.exception.ConflictException
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
    private val topicService: TopicService,
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
        // A trade's amount is derived from its quantity and price, never supplied.
        val amount = if (type == TransactionType.TRADE) null else requirePositive(request.amount, "amount")
        val account = accountService.getOwnedBy(accountId, owner)
        val occurredOn = resolveOccurredOn(request.occurredOn)
        val rate = resolveExchangeRate(request.exchangeRate, account)

        val transaction =
            when (type) {
                TransactionType.INCOME, TransactionType.EXPENSE -> {
                    buildSimple(
                        type = type,
                        amount = checkNotNull(amount),
                        rate = rate,
                        account = account,
                        occurredOn = occurredOn,
                        request = request,
                        owner = owner,
                    )
                }

                TransactionType.TRANSFER -> {
                    buildTransfer(
                        amount = checkNotNull(amount),
                        rate = rate,
                        account = account,
                        occurredOn = occurredOn,
                        request = request,
                        owner = owner,
                    )
                }

                TransactionType.TRADE -> {
                    buildTrade(rate = rate, account = account, occurredOn = occurredOn, request = request)
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
            when (transaction.type) {
                TransactionType.TRADE -> {
                    resolveTradePatch(request, transaction)
                }

                TransactionType.ADJUSTMENT -> {
                    rejectTradeFields(request.ticker, request.quantity, request.unitPrice)
                    request.amount?.let(::requireNonZeroAmount)
                }

                TransactionType.INCOME, TransactionType.EXPENSE, TransactionType.TRANSFER -> {
                    rejectTradeFields(request.ticker, request.quantity, request.unitPrice)
                    request.amount?.let { requirePositive(it, "amount") }
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
        if (newAmount != null || request.exchangeRate != null) {
            transaction.amountKzt = toKzt(transaction.amount, transaction.exchangeRate)
        }

        request.occurredOn?.let { transaction.occurredOn = resolveOccurredOn(it) }
        request.categoryId?.let { categoryId ->
            transaction.category =
                categoryId.orElse(null)?.let { resolveCategory(it, transaction.type, owner) }
        }
        request.topicId?.let { topicId ->
            val newTopic = topicId.orElse(null)
            if (newTopic != null && transaction.type !in TOPIC_TYPES) {
                throw invalidField("topicId", "is only valid for an INCOME or EXPENSE")
            }
            transaction.topic = newTopic?.let { topicService.getOwnedBy(it, owner) }
        }
        request.note?.let { transaction.note = it.orElse(null)?.let({ requireMaxLength(it, "note") }) }
        if (request.ticker != null || request.quantity != null) requireNothingOversold(transaction.account)
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
        if (transaction.type == TransactionType.TRADE) requireNothingOversold(transaction.account)
    }

    @Transactional(readOnly = true)
    override fun history(
        accountId: UUID,
        reader: User,
        from: LocalDate,
        to: LocalDate,
    ): List<Transaction> {
        // A read path, so a viewer of this account is a legitimate caller. Every
        // other method here keeps getOwnedBy, because every other one writes.
        val account = accountService.getReadableBy(accountId, reader).resource
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
        topicId: UUID?,
    ): List<Transaction> {
        val range = historyRange(from = from, to = to)
        // Resolved only to 404 on an id that isn't the caller's; the query
        // filters on the id itself.
        accountId?.let { accountService.getOwnedBy(it, owner) }
        categoryId?.let { categoryService.getOwnedBy(it, owner) }
        topicId?.let { topicService.getOwnedBy(it, owner) }
        return transactionRepository.findForOwner(
            owner = owner,
            from = range.from,
            to = range.to,
            accountId = accountId,
            categoryId = categoryId,
            topicId = topicId,
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
                    note = request.note?.let({ requireMaxLength(it, "note") }),
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
        rejectTradeFields(request.ticker, request.quantity, request.unitPrice, request.tradeSide)
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
            topic = request.topicId?.let { topicService.getOwnedBy(it, owner) },
            note = request.note?.let({ requireMaxLength(it, "note") }),
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
        if (request.topicId != null) {
            // Attaching one would count the withdrawal and what it paid for.
            throw invalidField("topicId", "is not valid for a TRANSFER")
        }
        rejectTradeFields(request.ticker, request.quantity, request.unitPrice, request.tradeSide)
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
            note = request.note?.let({ requireMaxLength(it, "note") }),
        )
    }

    /**
     * A trade is a ledger row like any other, so buying debits the account
     * through the same apply/reverse path. Only the fields that describe the
     * asset are its own; everything a category or a topic would say about it
     * is rejected, because a trade is not spending.
     */
    private fun buildTrade(
        rate: BigDecimal,
        account: Account,
        occurredOn: LocalDate,
        request: CreateTransactionRequest,
    ): Transaction {
        if (!account.type.holdsAssets) {
            throw invalidField("accountId", "must be a BROKER or CRYPTO account for a TRADE")
        }
        rejectOnTrade("amount", request.amount, "is derived from quantity × unitPrice for a TRADE")
        rejectOnTrade("toAccountId", request.toAccountId)
        rejectOnTrade("toAmount", request.toAmount)
        rejectOnTrade("categoryId", request.categoryId)
        rejectOnTrade("topicId", request.topicId)
        val side = request.tradeSide ?: throw invalidField("tradeSide", "is required for a TRADE")
        val ticker = normalizeTicker(request.ticker ?: throw invalidField("ticker", "is required for a TRADE"))
        val quantity = requireTradeFigure(request.quantity, "quantity")
        val unitPrice = requireTradeFigure(request.unitPrice, "unitPrice")
        if (side == TradeSide.SELL) requireHeld(account, ticker, quantity)
        val amount = tradeAmount(quantity, unitPrice)
        return Transaction(
            type = TransactionType.TRADE,
            amount = amount,
            currency = account.currency,
            exchangeRate = rate,
            amountKzt = toKzt(amount, rate),
            toAmount = null,
            occurredOn = occurredOn,
            account = account,
            toAccount = null,
            category = null,
            note = request.note?.let({ requireMaxLength(it, "note") }),
            tradeSide = side,
            ticker = ticker,
            quantity = quantity,
            unitPrice = unitPrice,
        )
    }

    /** Selling more than the account holds would leave a negative position. */
    private fun requireHeld(
        account: Account,
        ticker: String,
        quantity: BigDecimal,
    ) {
        val held =
            holdingsOf(transactionRepository.findTradesByAccount(account))
                .firstOrNull { it.ticker == ticker }
                ?.quantity ?: BigDecimal.ZERO
        if (quantity > held) {
            throw invalidField(
                "quantity",
                "exceeds the ${held.stripTrailingZeros().toPlainString()} $ticker held in this account",
            )
        }
    }

    /**
     * Correcting or deleting a purchase must not strand a sale that depended
     * on it: the sale's cash would stay in the balance with nothing sold.
     */
    private fun requireNothingOversold(account: Account) {
        val oversold =
            positionsOf(transactionRepository.findTradesByAccount(account))
                .firstOrNull { it.quantity.signum() < 0 }
        if (oversold != null) {
            throw ConflictException(
                "This would leave more ${oversold.ticker} sold than bought in this account; " +
                    "correct or delete the sale first",
            )
        }
    }

    /**
     * Applies a trade's own corrections and returns its re-derived amount, or
     * null when neither figure behind it changed. The caller moves the balance.
     */
    private fun resolveTradePatch(
        request: UpdateTransactionRequest,
        trade: Transaction,
    ): BigDecimal? {
        rejectOnTrade("amount", request.amount, "is derived from quantity × unitPrice for a TRADE")
        rejectOnTrade("toAmount", request.toAmount)
        request.ticker?.let { trade.ticker = normalizeTicker(it) }
        if (request.quantity == null && request.unitPrice == null) return null
        val quantity = request.quantity?.let { requireTradeFigure(it, "quantity") } ?: trade.quantity
        val unitPrice = request.unitPrice?.let { requireTradeFigure(it, "unitPrice") } ?: trade.unitPrice
        trade.quantity = quantity
        trade.unitPrice = unitPrice
        return tradeAmount(
            quantity = checkNotNull(quantity) { "A TRADE always has a quantity" },
            unitPrice = checkNotNull(unitPrice) { "A TRADE always has a unit price" },
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
        return request.toAmount?.let { requirePositive(it, "toAmount") }
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
                requirePositive(toAmount, "toAmount")
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
        validateExchangeRate(
            exchangeRate
                ?: if (account.currency == BASE_CURRENCY) {
                    BigDecimal.ONE
                } else {
                    throw invalidField(
                        "exchangeRate",
                        "is required for a ${account.currency} account: give the $BASE_CURRENCY value of 1 ${account.currency}",
                    )
                },
            account.currency,
        )

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
}

private const val MAX_TICKER_LENGTH = 32
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
        // ADJUSTMENT is the only type whose amount may be negative; the drift can go either way.
        TransactionType.INCOME, TransactionType.ADJUSTMENT -> {
            account.balance += signed
        }

        TransactionType.EXPENSE -> {
            account.balance -= signed
        }

        TransactionType.TRANSFER -> {
            val destination =
                checkNotNull(toAccount) { "A TRANSFER always has a destination account" }
            account.balance -= signed
            // Cross-currency transfers credit the destination its own amount.
            destination.balance += (toAmount ?: amount).multiply(BigDecimal(direction))
        }

        TransactionType.TRADE -> {
            when (checkNotNull(tradeSide) { "A TRADE always has a side" }) {
                TradeSide.BUY -> account.balance -= signed

                TradeSide.SELL -> account.balance += signed

                // Already held when tracking began, so no cash moves for it.
                TradeSide.OPENING -> Unit
            }
        }
    }
}

/** What a trade moves, in the account's currency. */
private fun tradeAmount(
    quantity: BigDecimal,
    unitPrice: BigDecimal,
): BigDecimal {
    val amount = quantity.multiply(unitPrice).setScale(MONEY_SCALE, RoundingMode.HALF_UP)
    if (amount.signum() == 0) throw invalidField("quantity", "is too small: the trade's total rounds to zero")
    return amount
}

/** Positive, and no finer than the column can hold — a silently rounded quantity would misstate a holding. */
private fun requireTradeFigure(
    value: BigDecimal?,
    field: String,
): BigDecimal {
    val figure = requirePositive(value, field)
    if (figure.stripTrailingZeros().scale() > PRICE_SCALE) {
        throw invalidField(field, "must have at most $PRICE_SCALE decimal places")
    }
    return figure
}

private fun normalizeTicker(ticker: String): String {
    val normalized = requireNonBlankName(ticker, "ticker").uppercase()
    if (normalized.length > MAX_TICKER_LENGTH) {
        throw invalidField("ticker", "must be at most $MAX_TICKER_LENGTH characters")
    }
    return normalized
}

private fun rejectOnTrade(
    field: String,
    value: Any?,
    message: String = "is not valid for a TRADE",
) {
    if (value != null) throw invalidField(field, message)
}

/** The fields that describe an asset belong to a `TRADE` and to nothing else. */
private fun rejectTradeFields(
    ticker: String?,
    quantity: BigDecimal?,
    unitPrice: BigDecimal?,
    tradeSide: TradeSide? = null,
) {
    val present =
        listOf("tradeSide" to tradeSide, "ticker" to ticker, "quantity" to quantity, "unitPrice" to unitPrice)
            .firstOrNull { it.second != null }
    if (present != null) throw invalidField(present.first, "is only valid for a TRADE")
}

private fun toKzt(
    amount: BigDecimal,
    exchangeRate: BigDecimal,
): BigDecimal = amount.multiply(exchangeRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP)

private fun requireNonZeroAmount(amount: BigDecimal): BigDecimal {
    if (amount.signum() == 0) throw invalidField("amount", "must not be zero")
    return amount
}
