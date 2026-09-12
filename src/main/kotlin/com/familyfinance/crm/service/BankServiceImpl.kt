package com.familyfinance.crm.service

import com.familyfinance.crm.domain.Bank
import com.familyfinance.crm.exception.NotFoundException
import com.familyfinance.crm.repository.BankRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BankServiceImpl(
    private val bankRepository: BankRepository,
) : BankService {
    @Transactional(readOnly = true)
    override fun listAll(): List<Bank> = bankRepository.findAllByOrderByNameAsc()

    @Transactional(readOnly = true)
    override fun getById(id: UUID): Bank = bankRepository.findById(id).orElseThrow { NotFoundException("Bank $id was not found") }

    @Transactional
    override fun findOrCreate(name: String): Bank {
        val trimmed = name.trim()
        return bankRepository.findByNameIgnoreCase(trimmed)
            ?: bankRepository.save(Bank(name = trimmed))
    }
}
