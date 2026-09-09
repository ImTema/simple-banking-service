# Simple Banking Service

A small in-memory library that simulates basic banking operations: creating accounts, depositing,
withdrawing, transferring between accounts, and checking balances. It's a software module, not a
deployable service with an API — callers use it directly through the `Bank` class.

## What it has

- **`Bank`** — the single entry point, exposing:
  - `createAccount(ownerId, initialDeposit)`
  - `deposit(accountId, amount)`
  - `withdraw(accountId, amount)`
  - `transfer(fromAccountId, toAccountId, amount)`
  - `getBalance(accountId)`
- **`Money`** — a currency-aware value object (`BigDecimal` amount + `java.util.Currency`) that
  never holds a negative amount.
- **`Account`** — id (UUID), owner reference, balance, and its own lock for thread-safe mutation.
- **`AccountRepository`** — a storage interface with an in-memory implementation
  (`InMemoryAccountRepository`) behind it, so persistence can be swapped in later without touching
  `Bank` or the domain model.
- Explicit result types instead of exceptions: every `Bank` operation returns a `BankResult`
  (`Success` or `Failure`), with failures naming the exact reason (`AccountNotFound`,
  `InvalidAmount`, `CurrencyMismatch`, `InsufficientFunds`, `SameAccountTransfer`).
- Thread safety: each `Account` holds its own lock; transfers lock both accounts in a consistent
  order to avoid deadlocks under concurrent use.

## Stack

- Kotlin, built with Maven
- JUnit 5 + `kotlin-test-junit5` for tests

## Out of scope

- Multiple accounts per user.
- Currency conversion — transfers require matching currencies; mismatches are rejected.
- A payment-intent/reservation model (held vs. available balance, webhook-driven capture/release,
  expiry, reconciliation) — a possible future direction if external payment-processor integration
  is ever added, but nothing here supports it yet.
- Any API layer (REST, gRPC, etc.) — this is a library, not a deployable service.
- Persistent storage — in-memory only, behind an interface so it can be swapped later.

See `specs/SPEC.md` for the full design spec.
