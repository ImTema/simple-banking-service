# Simple Banking Service — Spec

## Problem Statement

A developer wants to simulate basic banking operations — creating accounts, depositing, withdrawing, transferring between accounts, and checking balances — without standing up a real database or API. The exercise is meant to demonstrate domain modeling (DDD), thread safety, and clean separation between domain logic and storage, in a way that can be extended later (e.g. swapping the in-memory store for a real one, or adding payment-intent/webhook flows) without redesigning the core.

## Solution

Build a single-module Kotlin/Maven library exposing one entry point, `Bank`, which orchestrates account creation, deposits, withdrawals, transfers, and balance checks against an in-memory, thread-safe account store. Money is represented as a currency-aware value object with strict invariants. All operations return an explicit sealed `Result` type rather than throwing on expected failure conditions (insufficient funds, currency mismatch, etc.), so callers can handle outcomes without exception-driven control flow. The storage layer sits behind a repository interface so it can be swapped for a persistent implementation later without touching `Bank` or the domain model.

## User Stories

1. As a developer using the library, I want to create an account with an initial deposit, so that I can start simulating a customer's banking activity.
2. As a developer, I want to create an account with a zero initial deposit, so that I can model a customer opening an account before funding it.
3. As a developer, I want account creation to reject a negative initial deposit, so that invalid state can never enter the system.
4. As a developer, I want each account to have an owner reference (`ownerId`), so that I can associate an account with a customer identity even though the system only tracks one account per user.
5. As a developer, I want each account to be assigned a unique identifier automatically, so that I don't have to manage ID uniqueness myself.
6. As a developer, I want to deposit money into an existing account, so that I can simulate a customer adding funds.
7. As a developer, I want deposits of zero or negative amounts rejected, so that the balance can never be corrupted by a bad input.
8. As a developer, I want to withdraw money from an account, so that I can simulate a customer spending or moving funds out.
9. As a developer, I want a withdrawal that would overdraw the account rejected, so that the account can never go negative.
10. As a developer, I want withdrawals of zero or negative amounts rejected, so that the balance can never be corrupted by a bad input.
11. As a developer, I want to transfer money from one account to another, so that I can simulate a payment between two customers.
12. As a developer, I want a transfer rejected if either account doesn't have sufficient funds, so that no account can be overdrawn as a side effect of a transfer.
13. As a developer, I want a transfer between accounts in different currencies rejected, so that I don't silently lose or gain value through an unintended conversion.
14. As a developer, I want a transfer from an account to itself rejected, so that a no-op transfer can't be mistaken for a real one or used to bypass validation.
15. As a developer, I want transfers of zero or negative amounts rejected, so that the balance can never be corrupted by a bad input.
16. As a developer, I want to check an account's current balance at any time, so that I can verify the effect of operations or display state to a user.
17. As a developer, I want an operation on a non-existent account ID to return a clear "account not found" outcome, so that I can handle bad references without a crash.
18. As a developer, I want every failure case (not found, invalid amount, currency mismatch, insufficient funds, same-account transfer) to be a distinct, named outcome, so that calling code can branch on the exact reason without parsing strings or catching generic exceptions.
19. As a developer, I want concurrent deposits/withdrawals/transfers on the same or different accounts from multiple threads to leave balances consistent, so that the service is safe to use from a multi-threaded caller (e.g. simulating concurrent customer activity).
20. As a developer, I want a transfer that touches two accounts to never deadlock against another concurrent transfer touching the same two accounts in the opposite order, so that the system doesn't hang under concurrent load.
21. As a developer, I want the storage mechanism to live behind an interface, so that I can later swap the in-memory implementation for a persistent one without changing `Bank` or the domain model.
22. As a developer reading the README, I want a clear description of what the project is, what it supports, the tech stack, and what's explicitly out of scope, so that I understand its boundaries without reading all the code.

## Implementation Decisions

- **Language/build**: Kotlin on the existing Maven project (`pom.xml`), Kotlin 2.3.20, single module. No Gradle migration.
- **Package layout**: two packages under the project's base package — `domain` (`Account`, `Money`, `Currency` usage, sealed `Result`/error types) and `repository` (`AccountRepository` interface + `InMemoryAccountRepository`). `Bank` sits at the top level as the sole entry point.
- **`Bank`** (single class, no separate `AccountService`/`BankService` split): holds one `AccountRepository` instance, injected at construction. Exposes:
  - `createAccount(ownerId: String, initialDeposit: BigDecimal, currency: Currency): Result<Account, BankError>` — takes the raw amount/currency rather than a pre-built `Money`, so a negative initial deposit can be rejected as an `InvalidAmount` `Result` rather than throwing at `Money` construction time.
  - `deposit(accountId: AccountId, amount: Money): Result<Unit, BankError>`
  - `withdraw(accountId: AccountId, amount: Money): Result<Unit, BankError>`
  - `transfer(fromId: AccountId, toId: AccountId, amount: Money): Result<Unit, BankError>`
  - `getBalance(accountId: AccountId): Result<Money, BankError>`
- **`Account`**: `id: AccountId` (UUID, auto-generated), `ownerId: String`, mutable `balance: Money`, and its own `ReentrantLock` used to guard mutation. One account per owner (multiple accounts per user is out of scope; no uniqueness enforcement needed beyond UUID generation).
- **`Money`**: value object wrapping `BigDecimal` amount + `Currency`. Constructor throws if `amount < 0`. `plus`/`minus` require matching `Currency` on both operands and throw on mismatch — used only as an internal safety net after `Bank` has already validated currencies match at the operation level.
- **Currency handling**: no conversion between currencies anywhere in the system. A transfer between accounts of different currencies is rejected via the `CurrencyMismatch` result, not attempted.
- **Concurrency model**: lock lives on `Account`, not in the repository or a global lock. Single-account operations (deposit, withdraw) acquire that account's lock for the duration of the balance mutation. `transfer` acquires both accounts' locks in a consistent order (comparing `AccountId` values) before mutating either balance, to prevent deadlock against a concurrent transfer running in the opposite direction.
- **Repository**: `AccountRepository` interface with exactly two methods — `save(account: Account)` and `findById(id: AccountId): Account?`. No `existsById`, no `getAll` — nothing in the required operation set needs them. `InMemoryAccountRepository` backs this with an in-memory map; `Account` is mutated in place after being fetched, so `save` is only used to insert on creation.
- **Error/result model**: sealed `Result<T, BankError>` type (not exceptions) returned from every `Bank` method. `BankError` sealed hierarchy: `AccountNotFound`, `InvalidAmount` (amount ≤ 0 on any input, or negative initial deposit), `CurrencyMismatch` (transfer across differing currencies), `InsufficientFunds` (withdraw/transfer exceeds available balance), `SameAccountTransfer` (transfer where `fromId == toId`).
- **Validation boundaries**: initial deposit on account creation may be `>= 0`. All operation amounts (deposit, withdraw, transfer) must be `> 0`.
- **README.md**: brief description of what the project is, what it supports (the five `Bank` operations), the tech stack (Kotlin, Maven, JUnit5/kotlin-test), and an out-of-scope section (see below).

## Testing Decisions

- Tests exercise the single seam: construct `Bank` with a real `InMemoryAccountRepository` (no mocks — the in-memory repo is simple enough to use directly) and call `Bank`'s public methods, asserting on the returned `Result` and on subsequent `getBalance` calls. No test reaches into `Account` or the repository's internal map directly — only external behavior through `Bank` is verified.
- Framework: JUnit5 with `kotlin-test-junit5` assertions, matching what's already in `pom.xml`. No new test library added.
- Coverage areas, one test class per concern is acceptable:
  - Account creation (valid initial deposit, zero deposit, negative deposit rejected).
  - Deposit (valid, zero/negative rejected, unknown account).
  - Withdrawal (valid, insufficient funds rejected, zero/negative rejected, unknown account).
  - Transfer (valid, insufficient funds, currency mismatch, same-account rejected, zero/negative rejected, unknown account on either side).
  - Balance check (existing account, unknown account).
  - Concurrency: multiple threads performing deposits/withdrawals on the same account leave the final balance consistent with the sum of operations; concurrent transfers between the same two accounts in opposite directions complete without deadlock and leave balances consistent.
- No prior art exists in this repo yet (only `Main.kt` is present) — this establishes the first test conventions for the project.

## Out of Scope

- Multiple accounts per user.
- Currency conversion — transfers require matching currencies; mismatches are rejected, not converted.
- Payment-intent / reservation model: no `reservedAmount`/`availableAmount` split, no intent IDs, no webhook-driven capture/release flow, no expiry or reconciliation logic. Flagged as a possible future direction if external payment-processor integration is ever added, but nothing here is built to support it yet.
- Any API layer (REST, gRPC, etc.) — this is a library/module, not a deployable service.
- Persistent storage — in-memory only, behind an interface so it can be swapped later.
- Gradle migration — project stays on Maven.

## Further Notes

- No issue tracker is configured for this repo (no git remote, no tracker config found), so this spec is committed to the repo as `SPEC.md` rather than published externally.
- `AccountId` and `Currency` types are assumed to use idiomatic Kotlin value-holder types (e.g. inline/value classes or plain wrappers) — exact representation is an implementation detail left to the build step, not fixed here.
- The lock-ordering rule for transfers (compare `AccountId`s) must be applied consistently everywhere two accounts are locked together; if a future operation ever locks more than two accounts at once, the same total-ordering approach should be reused rather than inventing a new scheme.
