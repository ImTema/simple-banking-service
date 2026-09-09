package org.example.banking.domain

import java.util.UUID

@JvmInline
value class AccountId(val value: UUID) : Comparable<AccountId> {
    override fun compareTo(other: AccountId): Int = value.compareTo(other.value)

    companion object {
        fun generate(): AccountId = AccountId(UUID.randomUUID())
    }
}
