package org.example.banking.repository

import org.example.banking.domain.Account
import org.example.banking.domain.AccountId

interface AccountRepository {
    fun save(account: Account)
    fun findById(id: AccountId): Account?
}
