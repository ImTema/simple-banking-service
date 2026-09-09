package org.example.banking.domain

sealed class BankResult<out T> {
    data class Success<out T>(val value: T) : BankResult<T>()
    data class Failure(val error: BankError) : BankResult<Nothing>()
}
