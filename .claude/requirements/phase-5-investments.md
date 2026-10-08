# Phase 5 — Investments

Status: **built** (2026-10-08, migration `V9`). Rewritten that day from the
owner's own description of how they want to record investments; the earlier
draft (a shared `Instrument` table, a separate `trades` table, manual price
snapshots, trades that did not move cash) was never built and is superseded
by this document.

## Scope

Record what is bought and sold in a broker or crypto account, and show what is
held and what it cost. **Cost only, for now:** there is no current price and no
current exchange rate anywhere in the system, so nothing here says what a
holding is worth today. That is the next step, not an oversight — see
"Deferred" below.

## A trade is a transaction

`TRADE` is a fourth transaction type next to `INCOME`, `EXPENSE` and
`TRANSFER`, entered through the same `POST /api/v1/transactions` and stored in
the same `transactions` table. It is not a separate entity. The consequences
are what make this the right shape:

- **A trade moves cash.** Buying debits the account and selling credits it,
  through the same apply/reverse path every other transaction uses, so editing
  or deleting a trade corrects the balance automatically. Money reaches a
  broker account by `TRANSFER`, and leaves it for an asset by `TRADE`.
- **A trade appears in the account's history**, between the transfers that
  funded it, and is readable by a viewer of a shared account like any other row.
- **A trade is not spending.** It is excluded from the monthly summary, from
  budgets and from topics, and carries no category.

### Fields

On top of the common transaction fields, a `TRADE` carries four of its own.
All four are set on a trade and null on everything else (a `CHECK` enforces it).

- `tradeSide: enum` — `BUY`, `SELL`, `OPENING`
- `ticker: String` — free text, trimmed and stored uppercase, at most 32
  characters. **There is no instrument table.** Whether it is a stock, an ETF,
  a bond or a coin goes in the transaction's `note`.
- `quantity: BigDecimal` — `NUMERIC(28,10)`. Fractional on purpose: a crypto
  quantity is rarely whole.
- `unitPrice: BigDecimal` — `NUMERIC(28,10)`, the price of one unit **in the
  account's currency**. Ten decimals because a coin can cost a fraction of a
  cent.

`amount` is **derived**: `quantity × unitPrice`, rounded half-up to four
decimals. It is rejected if supplied, on create and on edit. A trade whose
total rounds to zero is rejected.

### Currency

An asset is bought in the currency of the account it is bought from. There is
no per-instrument currency and no instrument-to-account conversion: to buy
something priced in EUR, create a EUR broker account and buy it there.

`exchangeRate` is the existing transaction field with its existing meaning —
KZT per 1 unit of the account's currency — and the existing rule: required for
a non-KZT account, 1 for a KZT one. So every trade records what it cost in KZT
at the time (`amountKzt`).

### Accounts

- A `TRADE` is only valid on an account of type `BROKER` or `CRYPTO`.
  `CRYPTO` is a new account type for an exchange or wallet; it behaves like
  `BROKER` in every respect.
- Account currencies stay three letters, so a crypto account holding a
  stablecoin is recorded as `USD`.
- **The owner wants a broker account's operation form to offer only Transfer
  and Trade.** That is a frontend rule. The backend still accepts `INCOME` and
  `EXPENSE` on these accounts, so a dividend or a broker fee can be recorded
  if that is ever wanted, and `reconcile` works as on any account.

### The three sides

- `BUY` — debits the account by `amount`.
- `SELL` — credits the account by `amount`. Rejected when `quantity` exceeds
  what the account holds of that ticker.
- `OPENING` — an asset **already held before tracking began**. It counts toward
  the holding at the price entered, and moves **no cash**, because that cash
  left before the ledger started. This is how existing ETFs and crypto are
  brought in: one `OPENING` per ticker per account, with the quantity held and
  the average price paid. `occurredOn` may be any past date.

### Editing and deleting

- `PATCH` accepts `ticker`, `quantity`, `unitPrice`, `exchangeRate`,
  `occurredOn` and `note`. A changed quantity or price re-derives `amount` and
  re-applies the balance difference. Type, side and account are immutable —
  delete and recreate.
- A correction or a delete that would leave **more of a ticker sold than
  bought** in the account is a `409`: the sale's cash would otherwise stay in
  the balance with nothing sold. Correct or delete the sale first.
- The four trade fields are rejected on any transaction that is not a `TRADE`.

## Holdings

Derived on read from the trades, never stored — the same reasoning as budget
usage and goal progress. One holding per **account and ticker**:

- `quantity` — bought and opening quantities, less sold.
- `cost` / `costKzt` — what the units still held cost, in the account's
  currency and in KZT. KZT cost uses **each purchase's own rate**.
- `averagePrice` / `averagePriceKzt` — cost divided by quantity.

Cost basis is a **weighted average**, replayed oldest first: a sale takes its
share of the cost with it and leaves the average price of what remains
unchanged. Not FIFO — simpler, and adequate without per-lot tax reporting.
Realised gain on a sale is not reported anywhere yet.

A position sold down to nothing is not listed. Holdings of a soft-deleted
account are not listed.

Because `cost` is a sum of amounts already rounded to four decimals, the
average price of a tiny crypto quantity can differ from the price typed in the
last few decimals (61000.4965 rather than 61000.5). Display it rounded.

## Sharing

- `GET /api/v1/accounts/{id}/holdings` is readable by a viewer of a shared
  account: sharing a broker account shares what is in it, as it already shares
  the balance and the history (decided 2026-10-06).
- `GET /api/v1/investments` is **own-only**. An account shared with the caller
  never contributes to it, by the Phase 8 rule that a shared resource adds
  nothing to the viewer's own figures.
- Every write path resolves the account with `getOwnedBy`, as everywhere else.

## API

| Method | Path                              | Purpose |
|--------|-----------------------------------|---------|
| POST   | `/api/v1/transactions`            | record a trade: `type=TRADE`, `accountId`, `tradeSide`, `ticker`, `quantity`, `unitPrice`, `exchangeRate` (non-KZT account), optional `occurredOn`, `note` |
| PATCH  | `/api/v1/transactions/{id}`       | correct `ticker`, `quantity`, `unitPrice`, `exchangeRate`, `occurredOn`, `note` |
| DELETE | `/api/v1/transactions/{id}`       | soft delete; the balance is reversed and holdings recompute |
| GET    | `/api/v1/investments`             | every holding across the caller's own accounts, with totals |
| GET    | `/api/v1/accounts/{id}/holdings`  | the same shape for one account; readable by a viewer |

`TransactionResponse` gained `tradeSide`, `ticker`, `quantity` and `unitPrice`,
null on anything that is not a trade.

Both holdings endpoints return:

```json
{
  "holdings": [
    {
      "ticker": "VOO",
      "accountId": "…", "accountName": "Freedom USD", "accountType": "BROKER",
      "currency": "USD",
      "quantity": 2,
      "averagePrice": 110, "averagePriceKzt": 55866.67,
      "cost": 220, "costKzt": 111733.33
    }
  ],
  "totalsByCurrency": [{ "currency": "USD", "cost": 220, "costKzt": 111733.33 }],
  "totalCostKzt": 111733.33
}
```

Totals are grouped by currency; currencies are only ever added together in KZT.

## Deferred, not rejected

- **Current value.** The owner intends to find open APIs for exchange rates and
  for stock and crypto prices. When one is chosen, a latest price per ticker
  and a latest rate per currency are all that is missing to show value and
  unrealised gain next to cost. Nothing built here needs to change shape for it.
- **Realised gain** on a sale.
- **Fees.** There is no fee field; a fee can be folded into the price or
  recorded as an `EXPENSE` on the account.
- **Corporate actions** (splits, ticker changes). A ticker can be corrected
  per trade; a split means editing quantities and prices by hand.
