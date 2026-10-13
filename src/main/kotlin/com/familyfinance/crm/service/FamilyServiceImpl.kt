package com.familyfinance.crm.service

import com.familyfinance.crm.domain.BASE_CURRENCY
import com.familyfinance.crm.domain.Transaction
import com.familyfinance.crm.domain.User
import com.familyfinance.crm.dto.FamilyCategoryTotal
import com.familyfinance.crm.dto.FamilyMemberTotal
import com.familyfinance.crm.dto.FamilyPurchasesResponse
import com.familyfinance.crm.dto.toFamilyPurchase
import com.familyfinance.crm.dto.toRef
import com.familyfinance.crm.repository.TransactionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@Service
class FamilyServiceImpl(
    private val transactionRepository: TransactionRepository,
) : FamilyService {
    @Transactional(readOnly = true)
    override fun purchases(
        reader: User,
        from: LocalDate,
        to: LocalDate,
    ): FamilyPurchasesResponse {
        val range = historyRange(from = from, to = to)
        // No ownership check on purpose: the query's `isFamily` filter is the
        // whole access rule, and the reader only decides which rows are theirs.
        val purchases = transactionRepository.findFamilyPurchases(from = range.from, to = range.to)
        return FamilyPurchasesResponse(
            from = range.from,
            to = range.to,
            currency = BASE_CURRENCY,
            total = purchases.totalKzt(),
            byMember =
                purchases
                    .groupBy { it.account.owner }
                    .map { (member, theirs) -> FamilyMemberTotal(member = member.toRef(), total = theirs.totalKzt()) }
                    .sortedByDescending { it.total },
            // Categories are per member, so the same name from two members is
            // two rows; the household reads them as one line.
            byCategory =
                purchases
                    .groupBy { it.category?.name }
                    .map { (name, theirs) -> FamilyCategoryTotal(categoryName = name, total = theirs.totalKzt()) }
                    .sortedByDescending { it.total },
            purchases = purchases.map { it.toFamilyPurchase(reader) },
        )
    }
}

private fun List<Transaction>.totalKzt(): BigDecimal = fold(BigDecimal.ZERO) { sum, purchase -> sum + purchase.amountKzt }
