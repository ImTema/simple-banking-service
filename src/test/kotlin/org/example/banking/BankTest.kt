package org.example.banking

import org.example.banking.domain.Account
import org.example.banking.domain.AccountId
import org.example.banking.domain.BankError
import org.example.banking.domain.BankResult
import org.example.banking.domain.Money
import org.example.banking.repository.InMemoryAccountRepository
import java.math.BigDecimal
import java.util.Currency
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class BankTest {

    private val usd: Currency = Currency.getInstance("USD")

    private fun bank() = Bank(InMemoryAccountRepository())

    private fun money(amount: String, currency: Currency = usd) = Money(BigDecimal(amount), currency)

    private fun Bank.openAccount(ownerId: String, initialDeposit: String, currency: Currency = usd): Account =
        (createAccount(ownerId, BigDecimal(initialDeposit), currency) as BankResult.Success).value

    @Test
    fun `creates an account with the given owner and initial deposit`() {
        val bank = bank()

        val result = bank.createAccount("owner-1", BigDecimal("100.00"), usd)

        val account = assertIs<BankResult.Success<Account>>(result).value
        assertEquals("owner-1", account.ownerId.value)
        assertEquals(money("100.00"), account.balance)
    }

    @Test
    fun `rejects a blank owner id`() {
        val bank = bank()

        val result = bank.createAccount(" ", BigDecimal("100.00"), usd)

        assertEquals(BankResult.Failure(BankError.InvalidOwnerId), result)
    }

    @Test
    fun `rejects a negative initial deposit`() {
        val bank = bank()

        val result = bank.createAccount("owner-1", BigDecimal("-0.01"), usd)

        assertEquals(BankResult.Failure(BankError.InvalidAmount), result)
    }

    @Test
    fun `returns the current balance for an existing account`() {
        val bank = bank()
        val account = bank.openAccount("owner-1", "50.00")

        val result = bank.getBalance(account.id)

        assertEquals(BankResult.Success(money("50.00")), result)
    }

    @Test
    fun `returns account not found for an unknown account id`() {
        val bank = bank()

        val result = bank.getBalance(AccountId.generate())

        assertIs<BankResult.Failure>(result)
    }

    @Test
    fun `deposit increases the account balance`() {
        val bank = bank()
        val account = bank.openAccount("owner-1", "50.00")

        val result = bank.deposit(account.id, money("25.00"))

        assertEquals(BankResult.Success(Unit), result)
        assertEquals(BankResult.Success(money("75.00")), bank.getBalance(account.id))
    }

    @Test
    fun `deposit of zero amount is rejected`() {
        val bank = bank()
        val account = bank.openAccount("owner-1", "50.00")

        val result = bank.deposit(account.id, money("0"))

        assertEquals(BankResult.Failure(BankError.InvalidAmount), result)
    }

    @Test
    fun `deposit into an unknown account is rejected`() {
        val bank = bank()

        val result = bank.deposit(AccountId.generate(), money("10.00"))

        assertIs<BankResult.Failure>(result)
    }

    @Test
    fun `withdraw decreases the account balance`() {
        val bank = bank()
        val account = bank.openAccount("owner-1", "50.00")

        val result = bank.withdraw(account.id, money("20.00"))

        assertEquals(BankResult.Success(Unit), result)
        assertEquals(BankResult.Success(money("30.00")), bank.getBalance(account.id))
    }

    @Test
    fun `withdraw more than the balance is rejected`() {
        val bank = bank()
        val account = bank.openAccount("owner-1", "50.00")

        val result = bank.withdraw(account.id, money("50.01"))

        assertEquals(BankResult.Failure(BankError.InsufficientFunds), result)
        assertEquals(BankResult.Success(money("50.00")), bank.getBalance(account.id))
    }

    @Test
    fun `withdraw of zero amount is rejected`() {
        val bank = bank()
        val account = bank.openAccount("owner-1", "50.00")

        val result = bank.withdraw(account.id, money("0"))

        assertEquals(BankResult.Failure(BankError.InvalidAmount), result)
    }

    @Test
    fun `withdraw from an unknown account is rejected`() {
        val bank = bank()

        val result = bank.withdraw(AccountId.generate(), money("10.00"))

        assertIs<BankResult.Failure>(result)
    }

    @Test
    fun `transfer moves funds from one account to another`() {
        val bank = bank()
        val from = bank.openAccount("owner-1", "50.00")
        val to = bank.openAccount("owner-2", "10.00")

        val result = bank.transfer(from.id, to.id, money("20.00"))

        assertEquals(BankResult.Success(Unit), result)
        assertEquals(BankResult.Success(money("30.00")), bank.getBalance(from.id))
        assertEquals(BankResult.Success(money("30.00")), bank.getBalance(to.id))
    }

    @Test
    fun `transfer more than the balance is rejected`() {
        val bank = bank()
        val from = bank.openAccount("owner-1", "10.00")
        val to = bank.openAccount("owner-2", "10.00")

        val result = bank.transfer(from.id, to.id, money("10.01"))

        assertEquals(BankResult.Failure(BankError.InsufficientFunds), result)
        assertEquals(BankResult.Success(money("10.00")), bank.getBalance(to.id))
    }

    @Test
    fun `transfer between different currencies is rejected`() {
        val bank = bank()
        val eur = Currency.getInstance("EUR")
        val from = bank.openAccount("owner-1", "50.00")
        val to = bank.openAccount("owner-2", "10.00", eur)

        val result = bank.transfer(from.id, to.id, money("20.00"))

        assertEquals(BankResult.Failure(BankError.CurrencyMismatch), result)
    }

    @Test
    fun `transfer to the same account is rejected`() {
        val bank = bank()
        val account = bank.openAccount("owner-1", "50.00")

        val result = bank.transfer(account.id, account.id, money("10.00"))

        assertEquals(BankResult.Failure(BankError.SameAccountTransfer), result)
    }

    @Test
    fun `transfer of zero amount is rejected`() {
        val bank = bank()
        val from = bank.openAccount("owner-1", "50.00")
        val to = bank.openAccount("owner-2", "10.00")

        val result = bank.transfer(from.id, to.id, money("0"))

        assertEquals(BankResult.Failure(BankError.InvalidAmount), result)
    }

    @Test
    fun `transfer from an unknown account is rejected`() {
        val bank = bank()
        val to = bank.openAccount("owner-2", "10.00")

        val result = bank.transfer(AccountId.generate(), to.id, money("10.00"))

        assertIs<BankResult.Failure>(result)
    }

    @Test
    fun `transfer to an unknown account is rejected`() {
        val bank = bank()
        val from = bank.openAccount("owner-1", "50.00")

        val result = bank.transfer(from.id, AccountId.generate(), money("10.00"))

        assertIs<BankResult.Failure>(result)
    }

    @Test
    fun `concurrent deposits and withdrawals on the same account leave a consistent balance`() {
        val bank = bank()
        val account = bank.openAccount("owner-1", "1000.00")
        val threads = 20
        val opsPerThread = 100

        val executor = Executors.newFixedThreadPool(threads)
        val latch = CountDownLatch(threads)
        repeat(threads) {
            executor.submit {
                repeat(opsPerThread) {
                    bank.deposit(account.id, money("1.00"))
                    bank.withdraw(account.id, money("1.00"))
                }
                latch.countDown()
            }
        }
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(BankResult.Success(money("1000.00")), bank.getBalance(account.id))
    }

    @Test
    fun `concurrent opposite-direction transfers between two accounts do not deadlock`() {
        val bank = bank()
        val a = bank.openAccount("owner-1", "1000.00")
        val b = bank.openAccount("owner-2", "1000.00")
        val transfersPerDirection = 200

        val executor = Executors.newFixedThreadPool(2)
        val latch = CountDownLatch(2)
        executor.submit {
            repeat(transfersPerDirection) { bank.transfer(a.id, b.id, money("1.00")) }
            latch.countDown()
        }
        executor.submit {
            repeat(transfersPerDirection) { bank.transfer(b.id, a.id, money("1.00")) }
            latch.countDown()
        }
        val completed = latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assert(completed) { "transfers deadlocked" }
        assertEquals(BankResult.Success(money("1000.00")), bank.getBalance(a.id))
        assertEquals(BankResult.Success(money("1000.00")), bank.getBalance(b.id))
    }
}
