# 1. Phase 0: a plain Java domain

Date: 2026-10-30

Status: Accepted

## Context

Phase 0 builds the core of the `ledger` module before any framework, database or clock exists.
Its definition of done asks for three things: `Money` respects each currency's scale,
`JournalEntry` rejects an entry that does not balance in some currency, and the `Transfer` state
machine rejects invalid transitions. Everything lives in one Modulith module, in plain Java 25
(decision 12), whose records, sealed types and pattern switches the design relies on, and it must
stay importable by the adapters of phase 1 without carrying them in.

## Decision

**Money.** `Money` is a record of a `long` count of minor units and a `Currency`; the scale always
comes from the currency, never from a constant (decisions 15 and 31). Text is parsed strictly: the
whole string must match, only ASCII digits, and more decimals than the currency allows is
rejected rather than rounded. Arithmetic is exact and an overflow surfaces as `ArithmeticException`.
Applying a rate rounds `HALF_EVEN` to the currency scale, always. Rejected: `double`, a fixed scale
of two decimals, rounding the input.

**Errors.** Business rules raise unchecked errors that extend an abstract `DomainException`, one
type per rule with a fixed message, so the edge can translate all of them in one place. A null or
a currency without a scale is a programming error and raises the standard Java exceptions; an
arithmetic overflow on valid operands is a system error and is never wrapped. Rejected: checked
exceptions, a single generic error.

**Postings.** A `Posting` is an `AccountId` (its own type over a UUID, so it is never mixed up with
another identifier), a `Side` and an amount. `Side` is an enum because debit and credit have the
same shape. The side carries the direction, so the amount must be positive; that rule is shared
with transfers in a package-private `Amounts` utility. Rejected: a sealed `Debit`/`Credit`
hierarchy, signed amounts, a positivity check on `Money` itself (a negative balance is a valid
`Money`).

**Journal entries.** A `JournalEntry` keeps its own immutable copy of at least two postings and
balances per currency: each posting contributes its signed amount and every currency must net to
zero, so amounts in different currencies never offset each other. An entry with a single side is
an imbalance; it needs no rule of its own.

**Transfers.** `Transfer` is a sealed interface with one record per state: pending, posted, voided
and expired (decision 26). Only a posted transfer carries its journal entry, so a posted transfer
without one cannot be built. `TransferTerms` validates the accounts and amount once for every
state. Only a pending transfer has transitions, and each returns the exact state it reaches, so
moving a final state again does not compile. A transfer of unknown state, such as one loaded from
storage, goes through `apply(TransferAction)`, which rejects every action on a final state.
Transitions return new values; nothing mutates. Rejected: a single record with a status enum and a
nullable entry, a State pattern whose methods throw at run time.

**Shape of the code.** A switch over the sealed states is used when each case does something
different, as `status()` does; a method on each state is used when the answer depends on that
state's own data, as `journalEntry()` does. Repeated identical branches are a sign the switch is
the wrong tool.

**Quality as part of the design.** The domain imports nothing from Spring or persistence, and every
production package is null-marked and checked at compile time (decision 58); tests stay outside on
purpose, since they pass null to prove the runtime checks. Mutation testing runs on the
domain at a 100% threshold and the threshold is never lowered: a mutant no test can kill is
removed by changing the shape of the code, not by excluding it.

## Consequences

**Good.**
- Illegal states cannot be represented, and invalid transitions fail at compile time wherever the
  state is known.
- The whole domain is tested without a container, in seconds, and every rule has its own error.
- Phase 1 adds adapters around the domain and keeps its rules; what changes is what is deferred
  below.

**Costs.**
- Constructors will change signature when identities and dates arrive.
- A `PostedTransfer` built outside `post()` could carry an entry that does not match its terms;
  only persistence will reconstruct one that way and it must validate it.
- Posting the same pending value twice produces two entries until persistence adds locking and
  idempotency (decisions 29 and 30).
- Balancing sums signed amounts in posting order, so an entry with amounts near the limit of a
  `long` can be rejected with `ArithmeticException` in one order and accepted in another. It never
  accepts an entry that does not balance.

**Deferred, each until the change that first needs it.**

| What | Arrives with |
|---|---|
| `JournalEntryId`, `TransferId` and the UUIDv7 port | the first change that stores or loads them |
| Booking date and value date | the business day or persistence |
| `reversalOf` | the first real reversal |
| Hold deadline and a clock port | expiration, phase 1 step 7 |
| Direct transfers | `Account` with held balances |
| Partial capture | a real case that needs it |
| Fees | phase 1 step 7 |
| `Account` and its policies | phase 1 |
| A reason for voiding | the first caller that records one |
| A `PositiveAmount` type in signatures | a third type that moves money |
| Exposing `Money` to other modules | the second module that needs it |
| Subpackages per aggregate | the arrival of `Account` |

**Lessons.**
- Mutation testing shaped the design, not only the tests: it caught a return value nobody used in
  the shared positive amount check, and a switch whose branches were identical.
- A test is only as strong as the failure it was seen to catch: each rule was first seen failing,
  including a reversed journal entry that still balanced and a comparison by reference.
- Choosing an enum or a sealed type is a question of shape: the same shape is an enum, different
  data per case is a sealed type.
