Develop a service that simulates basic banking operations in a programming language of your choice. This service will manage accounts, process deposits, withdrawals, and transfers between accounts.

Requirements:

1. A class or set of functions that allow:
    - Account creation: Allow users to create an account with an initial deposit.
    - Deposit: Enable users to deposit money into their account.
    - Withdrawal: Allow users to withdraw money from their account, ensuring that overdrafts are not allowed.
    - Transfer: Enable transferring funds between accounts.
    - Account balance: Provide the ability to check the account balance.
2. Database:
    - In-memory data storage will suffice, no need to have a database alongside the project, but you can add one at your discretion

The word "service" here is used in a "software component/module" rather "deployable unit with an API" sense, no need to provide API for it.

Personal touches:
- Apply domain-driven design principles.
- Ensure thread safety.
- Isolate in-memory storage behind a dedicated abstraction, so it can be replaced with a real database later without touching the domain logic (separation of concerns).
- Provide a README.md with a brief description: what the project is, what it includes, the technology stack, and out-of-scope features.
- Support currency-aware amounts.
- Model core concepts as value objects.

current out-of-scope:
- multiple accounts per user