## Purpose

A contract is priced as a whole, like the proposal it comes from: a total split into installments, with no price per dependent. It replaces the price per item of the `contract-core` (`contract_item.monthly_amount`, its R6).

## ADDED Requirements

### Requirement: The contract item has no price

`contract_item` MUST NOT have a price column, and neither the contract request nor its response MUST carry a price per item. A `monthlyAmount` sent in a request body MUST be ignored. The contract's `total_amount` is what is billed, for all its dependents together.

#### Scenario: Siblings share one contract priced as a whole

- **WHEN** an admin creates a contract for two siblings with `totalAmount = 4200.00` and `installments = 12`
- **THEN** the contract has two items, neither with `monthlyAmount`
- **AND** its `installmentAmount` is `350.00`

### Requirement: The contract shows its installments

Every contract response MUST include `installmentAmount`, which is `totalAmount` divided by `installments` with the cents truncated, and `lastInstallmentAmount`, which takes the remainder, so the installments always add up to `totalAmount`. The proposal MUST compute both the same way.

#### Scenario: A total that does not split evenly

- **WHEN** a contract has `totalAmount = 1000.00` and `installments = 3`
- **THEN** its `installmentAmount` is `333.33` and its `lastInstallmentAmount` is `333.34`

### Requirement: The total has two decimal places and one cent per installment

The contract's `totalAmount` MUST have at most two decimal places, validated with Bean Validation on the request DTO (HTTP 400 `contract.amount.scale`), so the installments of the create response are the ones every later read returns. The total MUST cover at least one cent per installment, decided by the `ContractTermsPolicy` (HTTP 422 `contract.installments.below_one_cent`), so no installment is zero.

#### Scenario: A total with three decimal places is rejected

- **WHEN** an admin creates a contract with `totalAmount = 4200.009`
- **THEN** the system returns HTTP 400 with the message resolved from `contract.amount.scale`

#### Scenario: One cent per installment is the smallest total

- **WHEN** an admin creates a contract with `totalAmount = 0.12` and `installments = 12`
- **THEN** the contract is created with `installmentAmount = 0.01` and `lastInstallmentAmount = 0.01`

#### Scenario: A total below one cent per installment is rejected

- **WHEN** an admin creates a contract with `totalAmount = 0.11` and `installments = 12`
- **THEN** the system returns HTTP 422 with the message resolved from `contract.installments.below_one_cent`
- **AND** nothing is persisted
