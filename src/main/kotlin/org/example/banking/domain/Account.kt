package org.example.banking.domain

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Account(
    val id: AccountId,
    val ownerId: OwnerId,
    initialBalance: Money,
) {
    val lock: ReentrantLock = ReentrantLock()

    var balance: Money = initialBalance
        private set

    fun currentBalance(): Money = lock.withLock { balance }

    fun credit(amount: Money) {
        balance += amount
    }

    fun debit(amount: Money): BankResult<Unit> {
        if (amount > balance) return BankResult.Failure(BankError.InsufficientFunds)
        balance -= amount
        return BankResult.Success(Unit)
    }
}
