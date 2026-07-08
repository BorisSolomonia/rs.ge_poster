# Predictive Budgeting — Architecture & Risk Assessment

**Status:** Complete · **Deliverable:** Acceptance Criterion #2
**Companion to:** `forecasting-research.md`

---

## 1. Correcting the premise in the ticket

The ticket names *"database load when querying years of history"* as the primary architectural risk. **There is no database.** Camora persists everything as JSON files (`pom.xml` has no JPA/JDBC/Hibernate); bank history lives in `bank-ledger-bog.json` / `bank-ledger-tbc.json`, categorization in `cash-flow-*.json`, all under the config dir. So the real risks are different and must be named correctly, or we'd "mitigate" a problem we don't have while walking into the ones we do.

**The real risks are memory and CPU, in-process, per request** — and we have prior evidence: the cash-flow module has **OOM'd twice in production** on ~91k transactions, and both fixes were about *not re-reading/re-allocating per transaction*. The forecasting engine reads the *same* data over a *wider* window (years, not a month), so it walks straight into that blast radius unless designed around it.

---

## 2. How the engine fits the existing architecture

The forecast is a **pure read-model derived from data we already load**. It introduces no new data source:

```
                 ┌─────────────────────────────────────────┐
 bank ledgers →  │ SourceLedgerStore (JSON, already loaded) │
 categorization →│ CashFlowService.sourced()+resolve()      │  ← REUSED verbatim
                 └───────────────┬─────────────────────────┘
                                 │ per-category monthly/weekly actual series
                                 ▼
                     ┌───────────────────────┐
                     │ ForecastService        │  ← NEW (pure Java math)
                     │  baseline = seasonal×g │
                     │            ⊕ EWMA trend │
                     └───────┬───────────────┘
                             │ merge overrides at read time
                             ▼
         ┌───────────────────────────────────┐
         │ ForecastOverrideStore (JSON)       │  ← NEW (mirrors TransactionCategoryOverrideStore)
         └───────────────────────────────────┘
                             │
                             ▼   ForecastController → /api/.../budget  → Budget UI (editable grid)
```

**Design principles:**
1. **Reuse, don't duplicate.** The historical series is built with the *same* `sourced()`+`resolve()`+monthly-merge that `CashFlowService.matrix()` uses. We extract that aggregation into a small shared helper rather than re-implementing categorization (which is where the earlier OOMs lived).
2. **Overrides never mutate the baseline.** They are a separate file, merged at read time. Recomputation stays truthful; "reset to algorithm" is always possible.
3. **The baseline function is an interface.** A future `ProphetForecaster` can replace `SeasonalGrowthForecaster` without touching storage or UI.

---

## 3. Risk register & mitigations

| # | Risk | Why it's real here | Mitigation |
|---|---|---|---|
| **R1** | **OOM on wide historical scan** | Forecast needs 12–18+ months; `sourced()` over years could hold ~91k+ `SourcedTransaction` in heap; we've OOM'd twice already | Build the per-category **monthly series once**, then discard raw transactions before forecasting. Never hold two wide ranges at once. Reuse the existing per-request `ResolutionContext` (loads rule/category/override files **once**, not per-txn — the exact fix from OOM #2). |
| **R2** | **Repeated recompute on every page view** | Matrix + forecast over years is CPU-heavy if recomputed per keystroke/request | **Caffeine cache** the computed historical series keyed by range (mirror the existing 60s `sourceCache`). Forecast math itself is microseconds once the series exists. |
| **R3** | **Slowing the existing cash-flow page** | Shared `SourceLedgerStore`/locks; a heavy budget query could contend | Forecast is **read-only**; reuses the same read path and cache. No new write contention on bank data. Override writes go to a **separate file** with its own lock — zero contention with cash-flow. |
| **R4** | **Wide `syncBank` triggering live bank API calls** | `sourced()` can hit TBC/BOG; a years-wide sync on every forecast would be slow and rate-limit-prone | Forecasting reads **already-synced local ledgers** for the historical window (append-only, per the supplier-debts caching design); it does **not** force a fresh multi-year bank fetch. Sync cadence stays owned by cash-flow. |
| **R5** | **Corrupt/partial override file** | Same failure mode as other JSON stores | Mirror `TransactionCategoryOverrideStore`'s **atomic temp-file + `ATOMIC_MOVE`** write and **quarantine-on-corrupt** load. Proven pattern already in the codebase. |
| **R6** | **Divide-by-tiny prior-year → absurd forecast** | Growth ratio explodes when last year's period ≈ 0 | Growth factor **clamped to [0.5, 2.0]**; degradation ladder falls back to trend-only when no prior-year point (see research doc §3.1). |
| **R7** | **Forecast reads 0 until income/expense is categorized** | Baseline derives from *categorized* history; uncategorized noise excluded (same definition sales-analysis uses) | Surface an explicit UI hint when history is uncategorized, pointing to `/cash-flow` — consistent with the sales-analysis behavior already shipped. |
| **R8** | **Concurrent forecast requests duplicating the wide load** | Two managers open the page at once → double heap | Reuse the **atomic single-flight** cache pattern already in `sourced()` (concurrent requests for one range share one load). |

---

## 4. Performance budget

- **Historical series build:** one pass over the range's transactions (same cost as one `matrix()` call, already acceptable in prod after the OOM fixes). Cached 60s.
- **Forecast math:** O(categories × forecast-periods) of `BigDecimal` arithmetic — negligible (sub-millisecond).
- **Override read/merge:** one small JSON file (bounded by #categories × #future-periods the user has touched — tens to low hundreds of entries).
- **Net effect on existing pages:** none. No new writes to shared bank data, no new heavy path added to cash-flow, no new background jobs.

**Memory ceiling note:** backend container is at 1G (raised from 768M during OOM #2). The forecast adds one cached series map (small — aggregated numbers, not raw txns) on top of the existing source cache. If a genuinely multi-year window is ever needed, we cap/aggregate at the monthly level *before* caching, so cache size is bounded by months×categories, not transactions.

---

## 5. Go / no-go

**Go.** The engine is a read-model over data we already ingest, using math that adds no dependencies and no services, and it explicitly reuses the two patterns that resolved our prior OOMs (per-request context loading + single-flight range cache). The only genuinely new persistent state is a small, separately-locked override file that follows a store pattern already proven in this codebase. No identified risk requires infrastructure change; all are mitigated by reusing existing patterns.
