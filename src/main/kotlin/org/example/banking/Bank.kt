package org.example.banking

import org.example.banking.domain.Account
import org.example.banking.domain.AccountId
import org.example.banking.domain.BankResult
import org.example.banking.domain.BankError
import org.example.banking.domain.Money
import org.example.banking.domain.OwnerId
import org.example.banking.repository.AccountRepository
import java.math.BigDecimal
import java.util.Currency
import kotlin.concurrent.withLock

class Bank(private val repository: AccountRepository) {

    fun createAccount(ownerId: String, initialDeposit: BigDecimal, currency: Currency): BankResult<Account> {
        if (ownerId.isBlank()) return BankResult.Failure(BankError.InvalidOwnerId)
        if (initialDeposit < BigDecimal.ZERO) return BankResult.Failure(BankError.InvalidAmount)
        val account = Account(AccountId.generate(), OwnerId(ownerId), Money(initialDeposit, currency))
        repository.save(account)
        return BankResult.Success(account)
    }

    fun getBalance(accountId: AccountId): BankResult<Money> =
        repository.findById(accountId)?.let { BankResult.Success(it.currentBalance()) }
            ?: BankResult.Failure(BankError.AccountNotFound(accountId))

    fun deposit(accountId: AccountId, amount: Money): BankResult<Unit> {
        if (!amount.isPositive()) return BankResult.Failure(BankError.InvalidAmount)
        val account = repository.findById(accountId)
            ?: return BankResult.Failure(BankError.AccountNotFound(accountId))
        account.lock.withLock { account.credit(amount) }
        return BankResult.Success(Unit)
    }

    fun withdraw(accountId: AccountId, amount: Money): BankResult<Unit> {
        if (!amount.isPositive()) return BankResult.Failure(BankError.InvalidAmount)
        val account = repository.findById(accountId)
            ?: return BankResult.Failure(BankError.AccountNotFound(accountId))
        return account.lock.withLock { account.debit(amount) }
    }

    fun transfer(fromId: AccountId, toId: AccountId, amount: Money): BankResult<Unit> {
        if (!amount.isPositive()) return BankResult.Failure(BankError.InvalidAmount)
        if (fromId == toId) return BankResult.Failure(BankError.SameAccountTransfer)

        val from = repository.findById(fromId) ?: return BankResult.Failure(BankError.AccountNotFound(fromId))
        val to = repository.findById(toId) ?: return BankResult.Failure(BankError.AccountNotFound(toId))

        if (from.balance.currency != to.balance.currency || amount.currency != from.balance.currency) {
            return BankResult.Failure(BankError.CurrencyMismatch)
        }

        val (first, second) = if (fromId < toId) from to to else to to from
        return first.lock.withLock {
            second.lock.withLock {
                when (val debited = from.debit(amount)) {
                    is BankResult.Failure -> debited
                    is BankResult.Success -> {
                        to.credit(amount)
                        BankResult.Success(Unit)
                    }
                }
            }
        }
    }

    private fun Money.isPositive(): Boolean = amount > BigDecimal.ZERO
}
