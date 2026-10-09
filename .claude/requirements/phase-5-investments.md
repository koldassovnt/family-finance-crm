# Phase 5 — Investments

Status: **built** (2026-10-08, migration `V9`). Rewritten that day from the
owner's own description of how they want to record investments; the earlier
draft (a shared `Instrument` table, a separate `trades` table, manual price
snapshots, trades that did not move cash) was never built and is superseded
by this document.

## Scope

Record what is bought and sold in a broker or crypto account, and show what is
held and what it cost. What a holding is worth today comes from an external
price API — see "Market data" below — and is absent, never guessed, when no
price is known.

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
- `ticker: String` — trimmed and stored uppercase, in the format its account
  requires (see "Ticker format"). **There is no instrument table.** Whether it
  is a stock, an ETF, a bond or a coin goes in the transaction's `note`.
- `quantity: BigDecimal` — `NUMERIC(28,10)`. Fractional on purpose: a crypto
  quantity is rarely whole.
- `unitPrice: BigDecimal` — `NUMERIC(28,10)`, the price of one unit **in the
  account's currency**. Ten decimals because a coin can cost a fraction of a
  cent.

`amount` is **derived**: `quantity × unitPrice`, rounded half-up to four
decimals. It is rejected if supplied, on create and on edit. A trade whose
total rounds to zero is rejected.

### Ticker format

Decided by the owner on 2026-10-09: one spelling per kind of account, enforced
on create and on edit (400 on `ticker`). It is what lets a holding be matched
to a price with no instrument table. The frontend must apply the same rules.

| Account | Format | Example | Rule |
|---------|--------|---------|------|
| `CRYPTO` | `COIN/CUR` | `TON/USD` | `CUR` must be the account's currency |
| `BROKER`, any currency but KZT | `SYMBOL.EXCHANGE` | `VEA.US` | exchange suffix of 1–6 letters |
| `BROKER` in KZT | plain | `HSBK`, `KZTO` | letters and digits only |

Existing rows are not rewritten; the rule applies to what is entered from now on.

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

## Market data — current value

Built 2026-10-09 (migration `V10`) and run that day against the real API with
the owner's key, on a throwaway database.

The owner chose **API Ninjas** (`api-ninjas.com`), called through a **Feign
client**, with **at most 30 requests a day to each of its three APIs**. This
is the service's only outbound dependency.

| What | Ticker as stored | Call | Quote stored as |
|------|------------------|------|-----------------|
| Exchange rate | — | `GET /v1/exchangerate?pair=USD_KZT` | `CURRENCY` / `USD`, price = KZT per 1 USD |
| Stock, ETF, bond | `VEA.US` | `GET /v1/stockprice?ticker=VEA` | `STOCK` / `VEA.US`, in the currency the API reports |
| Coin | `BTC/USD` | `GET /v1/cryptoprice?symbol=BTCUSDT` | `CRYPTO` / `BTC/USD`, currency `USD` |

- **Which API a ticker goes to is decided by the account type**: a holding in
  a `CRYPTO` account is a coin, one in a `BROKER` account is a stock.
- **A dollar coin pair is asked for against USDT** — the owner's rule: "USD is
  USDT in that case". `BTC/USD` → `BTCUSDT`.
- **A US ticker is sent without its suffix** (`VEA.US` → `VEA`); any other
  exchange suffix is sent as written (`HSBK.IL`), which is the API's own
  spelling for non-US listings.
- **A quote only values a holding in the same currency.** A stock the API
  quotes in GBP, held in a USD account, gets no value rather than a wrong one.
- **What is fetched:** every ticker anyone still holds in a non-KZT account,
  and every currency other than KZT that any live account is in. Rates are
  fetched for all account types, not only investment ones, so Phase 6 can use
  them.
- **Holdings in a KZT account are never sent**, because the answer is known to
  be "unknown" and would cost a call a day — see below.

### What the real API returned (2026-10-09, free plan)

| Asked | Result |
|-------|--------|
| `USD_KZT`, `EUR_KZT` | rate returned |
| `VEA` | 69.86 USD, exchange AMEX |
| `VEA.US` | 400 — hence the suffix is stripped |
| `BTCUSDT`, `ETHUSDT`, `ETHUSD` | price returned |
| `TONUSDT`, `TONUSD`, `TONUSDC`, `TONBTC`, `TONCOINUSDT` | 400 — not available under that name |
| `GRAMUSDT`, `GRAMUSD` | price returned (1.398). The owner says TON was renamed GRAM; that it is the same asset is their statement, not something this API confirms |
| `KZTO`, `HSBK`, `KZTO.KZ` | 400 — **KASE is not covered** |
| `HSBK.IL`, `HSBK.L` | returned, but these are the London GDR (USD / GBP), a different instrument from the KASE share |
| `KSPI` | returned (NASDAQ, USD) |

So for this owner today: rates, US-listed stocks and ETFs, and coins are
priced — TON under its new name, `GRAM/USD` — and **everything held in the KZT
broker account is not and will not be** from this provider. The owner decided
on 2026-10-09 to leave KASE unpriced: those holdings show cost only.

### Renaming a ticker

An asset can change its name (TON → GRAM), and a price is looked up by ticker,
so `POST /api/v1/accounts/{id}/holdings/rename` with `{"from": "TON/USD",
"to": "GRAM/USD"}` rewrites the ticker on **every** trade of it in that
account in one step — one at a time would split the holding in two. Owner
only. `to` must be in the account's ticker format; `from` is matched as
written, so rows that predate the format can still be renamed. A `to` already
traded in the account is a 409: merging two histories could not be undone by
renaming back. It answers with the account's holdings. The new name has no
price until the next refresh.

### The cap

`app.market-data.daily-limit` (30) applies to each API separately and is
counted in the `market_data_usage` table, one row per API per day, so it holds
across restarts and manual refreshes. A call is counted **before** it is made:
a request that fails has still spent quota. When there are more symbols than
the cap allows, the never-fetched and then the stalest go first, so the next
day continues where this one stopped.

A symbol already fetched **today** is not asked for again (`upToDate` in the
refresh answer): the source moves once a day, so a second ask buys the same
answer. A symbol that failed has no quote from today, so it is retried on each
refresh, within the cap.

API Ninjas' own free-plan limits are 3,000 requests a month and 100 an hour;
90 a day at most is 2,700 a month.

### When

- A scheduled job, daily at 08:00 `Asia/Almaty` (`app.market-data.refresh-cron`).
  Once a day is all the free plan is worth: it serves the last session's
  closing price and a once-daily rate.
- `POST /api/v1/market-data/refresh` runs the same refresh on demand, under the
  same cap.
- **Reading never calls the API.** Holdings are valued from stored quotes only.

### Storage and failure

`market_quotes` holds the **latest** quote per symbol, overwritten on each
refresh — no price history (a trend is Phase 6's job, and it snapshots totals).
A failed call, an unknown symbol or an answer with no price leaves the previous
quote in place: stale and dated beats absent. Every holding carries
`priceAsOf` so staleness is visible.

With no `API_NINJAS_KEY` the feature is off: nothing is called, the refresh
reports `configured: false`, and holdings report cost only, exactly as before.

**Plan terms:** the API Ninjas pricing page lists the free plan as
non-commercial, attribution required, and "data caching not allowed". Storing
the latest quote is what a daily cap requires. **Decided by the owner on
2026-10-09: this is acceptable**, because the app is a private household tool
and not a public or production service. Revisit only if it is ever opened up
beyond the family.

### What it adds to the API

Each holding gains `price`, `priceAsOf`, `exchange`, `value`, `valueKzt`,
`gain`, `gainKzt`. `exchange` is where the stock trades as the price API names
it (`AMEX` for `VEA.US`) — display only, stored from the same call that
fetched the price, and null for coins and for anything unpriced (the owner's
choice on 2026-10-09 over a hand-typed field). All null until a price in the
holding's currency exists; `valueKzt`
and `gainKzt` also need the currency's rate. `valueKzt` uses the **latest**
rate while `costKzt` used each purchase's own, so `gainKzt` reflects the
exchange rate moving as well as the price.

`totalsByCurrency` rows gain `value`, `valueKzt`, `gain`, `gainKzt` and
`unpriced`; the response gains `totalValueKzt`, `totalGainKzt` and `unpriced`.
**The market totals cover only the holdings that have a price, and `unpriced`
counts the ones left out.** Some holdings are never priced (TON, the KZT
broker account), and they must not blank the total for everything else. Two
consequences the frontend has to respect:

- when `unpriced > 0`, show that the value is partial — "N holdings not priced";
- the gain of a total is its `gain` field, **not** `value − cost`: `cost`
  covers every holding and `value` only the priced ones.

A market total is null only when nothing in it is priced.

| Method | Path                           | Purpose |
|--------|--------------------------------|---------|
| GET    | `/api/v1/market-data/rates`    | latest KZT rate per currency, with `fetchedAt` |
| POST   | `/api/v1/market-data/refresh`  | fetch now; returns `{configured, updated, upToDate, failed, overBudget}` |

## Deferred, not rejected

- **Realised gain** on a sale.
- **Price history**, and pre-filling a transaction's rate from the stored one.
- **Fees.** There is no fee field; a fee can be folded into the price or
  recorded as an `EXPENSE` on the account.
- **Corporate actions** (splits, ticker changes). A ticker can be corrected
  per trade; a split means editing quantities and prices by hand.
