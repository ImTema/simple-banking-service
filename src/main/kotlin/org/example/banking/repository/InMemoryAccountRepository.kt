package org.example.banking.repository

import org.example.banking.domain.Account
import org.example.banking.domain.AccountId
import java.util.concurrent.ConcurrentHashMap

class InMemoryAccountRepository : AccountRepository {
    private val accounts = ConcurrentHashMap<AccountId, Account>()

    override fun save(account: Account) {
        accounts[account.id] = account
    }

    override fun findById(id: AccountId): Account? = accounts[id]
}
