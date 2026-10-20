# When prices and rates are fetched

Prices come from two outside sources: API Ninjas (US stocks, coins and
exchange rates, at most 30 calls a day per API) and kase.kz (stocks held in a
KZT account). Neither is asked for a price that cannot have changed, so a
market that is shut costs no calls. Times are Almaty time.

## By day

| | Tue–Fri | Saturday | Sunday | Monday |
|---|---|---|---|---|
| US stocks (API Ninjas) | fetched | fetched (Friday's close) | skipped | skipped |
| KASE stocks | fetched | skipped | skipped | fetched |
| Coins (e.g. GRAM/USD) | fetched | fetched | fetched | fetched |
| Exchange rates (USD, EUR → KZT) | fetched | fetched | fetched | fetched |

**Why US stocks are fetched on Saturday and not Monday.** Wall Street closes
at 01:00–02:00 Almaty time, so each weekday's close is there the next
morning. Saturday's 08:00 run gets Friday's close; Sunday's and Monday's would
only get the same number again.

**Coins** trade every day. **Exchange rates** barely move at the weekend, but
they cost two calls a day and are kept daily by choice.

## The runs

| Run | When | Asks |
|---|---|---|
| Morning refresh | 08:00 every day | API Ninjas, and KASE for any ticker without a price from today |
| KASE close | 18:30 every day | KASE only, replacing the morning price with the close |
| After start | ~20 seconds after the app starts | same as the morning refresh |
| Manual | `POST /api/v1/market-data/refresh` | same as the morning refresh |

The scheduled times are set in `application.yml` (`app.market-data.refresh-cron`
and `kase-refresh-cron`). The weekend rule is in `MarketDataServiceImpl`, not
in those times, so every run follows it, the manual one included. On a skipped
day a run still starts; it just reports those stocks as `upToDate`.

## What is fetched anyway

- **Anything with no price yet**, such as a stock bought on Sunday, is fetched
  on any day.
- **A missed fetch is made up.** If Saturday's run failed or the app was
  down, a US stock last priced on Friday is fetched on Sunday or Monday, so
  Friday's close is not lost until Tuesday. The same goes for a KASE stock
  with no price from Friday on.
- **Within a day**, a symbol already fetched is not asked for again, except by
  the 18:30 KASE run on weekdays.

## Not covered

Exchange holidays are not known. On one, a run makes the usual call and gets
the previous close back.

The full rules, including the daily cap and what happens when a source fails,
are in "Market data — current value" and "KASE" in
[`.claude/requirements/phase-5-investments.md`](../.claude/requirements/phase-5-investments.md).
