package org.example.banking.domain

sealed class BankError {
    data class AccountNotFound(val accountId: AccountId) : BankError()
    data object InvalidAmount : BankError()
    data object CurrencyMismatch : BankError()
    data object InsufficientFunds : BankError()
    data object SameAccountTransfer : BankError()
}
