package org.example.banking.domain

@JvmInline
value class OwnerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Owner id must not be blank" }
    }
}
