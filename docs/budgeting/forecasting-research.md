# Predictive Budgeting — Algorithm Research & Golden Path

**Status:** Research complete · **Deliverable:** Acceptance Criterion #1
**Author:** Claude (Camora ERP) · **Scope:** week + month forecasts of cash inflows (sales) and outflows (expenses)

---

## 0. The constraint that drives everything

Before comparing algorithms, the honest starting point is *our data*, not a textbook:

| Reality | Value | Source |
|---|---|---|
| History start | **2025-01-01** | `CAMORA_ORGANIZATION_OPENING_DATE` (`application.yml`) |
| History available today | **~18 months** | today is mid-2026 |
| Complete seasonal cycles | **~1.5** (one full year + a partial second) | derived |
| Prior-year comparison points per calendar period | **exactly 1** | derived |
| Runtime | **JVM (Java 21), Spring Boot** — no Python, no R | `pom.xml` |
| Persistence | **File-based JSON**, no database | `pom.xml` has no JPA/JDBC |
| Existing stats/ML libraries | **None** (Jackson, Caffeine, POI, OpenCSV only) | `pom.xml` |
| Data volume | **tens of thousands** of bank transactions (~91k across BOG+TBC) | `CashFlowService` comments, prior OOM incidents |
| Users | **Non-technical management** of a Georgian SMB | project context |

**Implication:** the binding constraint is not model sophistication — it is that **we have ~1.5 seasonal cycles**. Any method that needs to *estimate* a seasonal shape from data (SARIMA seasonal terms, Holt-Winters seasonal component, Prophet's Fourier seasonality) is being asked to fit a yearly pattern it has only seen once. It will happily produce a curve, but that curve is not evidence — it is one year of noise dressed as signal. This is the single most important finding in this report.

A secondary hard constraint: **management must be able to override every number** (explicit requirement). This makes *explainability* a first-class requirement, not a nice-to-have. A forecast a manager cannot reason about is a forecast they cannot sensibly correct.

---

## 1. The five candidate approaches

### A. Seasonal-naive with growth ("period-over-period × growth rate")
Forecast for a future period = **the same period last year × a growth factor**, where the growth factor is estimated from recent year-over-year change. This is the literal restatement of the ticket's own phrasing — "previous year period differences, growth rates, and seasonality."

- **Data appetite:** minimal — needs 1 prior-year point + a few recent months. Works *today*.
- **Seasonality:** captured implicitly and exactly — last February already *contains* February's seasonality; no seasonal shape has to be estimated.
- **Explainability:** total. "Feb 2026 sales ≈ Feb 2025 (₾X) × 1.12 growth = ₾Y." A manager can verify and override with confidence.
- **Weakness:** if the single prior-year period was itself anomalous (a one-off big invoice, a closure), it propagates. Mitigated by blending with recent trend (see Golden Path) and by overrides.

### B. Exponential smoothing (Holt / Holt-Winters)
EWMA-based. **Holt** adds a trend term; **Holt-Winters (HW)** adds a seasonal term.

- **Holt (trend only):** cheap, ~15 lines of Java, great for *recent-trend* extrapolation. But no seasonality — useless alone for a seasonal business's month-ahead number.
- **Holt-Winters (seasonal):** needs **≥2 full cycles** to initialise the seasonal indices reliably; with 1.5 cycles the seasonal component is unstable and can oscillate. Standard forecasting guidance (Hyndman) is explicit that HW seasonal needs at least two seasons of data.
- **Explainability:** moderate (three smoothing constants α, β, γ — opaque to a manager).

### C. ARIMA / SARIMA
Auto-regressive integrated moving average; **SARIMA** adds seasonal terms.

- **Data appetite:** SARIMA seasonal differencing at lag 12 consumes a full year *before fitting even starts*; robust order selection wants multiple cycles. With 18 monthly points, parameter estimates have enormous variance.
- **Runtime cost:** no Java-native production-grade auto-ARIMA. Would mean either a heavyweight port or a Python sidecar service — a new deployable, on a VM that has already OOM'd twice.
- **Explainability:** low. `SARIMA(1,1,1)(0,1,1)₁₂` means nothing to management.

### D. Prophet (Meta's additive decomposition)
Trend + Fourier seasonality + holidays, fit via Stan.

- **Purpose-built** for business time series with holidays — genuinely attractive *on paper* for a seasonal SMB.
- **Data appetite:** its own guidance recommends **at least one, ideally several years**; Fourier seasonality from 1.5 cycles overfits.
- **Runtime cost:** Python/Stan. That is a **new service, new container, new failure mode, new memory pressure** on the same constrained VM. Disproportionate for a small internal tool.
- **Explainability:** decomposition plots exist but the fitted components are not something a manager overrides line-by-line.

### E. Machine-learning regressors (gradient boosting, LSTM)
Feature-engineered XGBoost/LightGBM, or a small LSTM.

- **Data appetite:** the worst fit — hundreds+ of examples wanted; we have ~18 monthly / ~78 weekly points per series, and *per category* far fewer non-zero points. Guaranteed overfit.
- **Runtime cost:** Python + model artifacts + retraining pipeline. Heaviest option.
- **Explainability:** lowest. A black box the user is required to override is a contradiction.

---

## 2. Stress test against our use case

Scored 1–5 (5 = best) on the axes that actually matter *here*.

| Axis (weight) | A. Seasonal-naive×growth | B. Holt-Winters | C. SARIMA | D. Prophet | E. ML |
|---|:--:|:--:|:--:|:--:|:--:|
| **Works with ~1.5 cycles (×3)** | 5 | 2 | 1 | 2 | 1 |
| **Captures seasonality (×3)** | 5 | 4 | 4 | 5 | 4 |
| **Explainable / overridable (×3)** | 5 | 3 | 1 | 2 | 1 |
| **Pure-JVM, no new service (×2)** | 5 | 5 | 2 | 1 | 1 |
| **Handles sparse per-category series (×2)** | 4 | 2 | 2 | 3 | 2 |
| **Non-technical maintainer (×1)** | 5 | 3 | 1 | 2 | 1 |
| **Weighted total (max 70)** | **68** | 44 | 30 | 41 | 27 |

### Stress scenarios walked through
1. **"Forecast next month's sales" (Feb 2026), one prior Feb exists.** A: uses Feb 2025 × growth — direct and correct. HW/SARIMA/Prophet: estimating a 12-period seasonal shape from one observed cycle → the February index is indistinguishable from noise.
2. **A category with sparse activity (e.g. quarterly tax payments).** A: same-period-last-year naturally lands the forecast in the right month; recent-trend blend near-zero elsewhere. ML/ARIMA: fit garbage on mostly-zero series.
3. **A brand-new category (added last month, no prior year).** A: gracefully **degrades** to recent-trend/EWMA (documented fallback). Seasonal models: undefined — no prior cycle to seed.
4. **Manager knows a ₾50k one-off invoice lands next week that the model can't know.** *Every* algorithm fails this — which is exactly why the ticket mandates manual override. The winner is the one whose baseline the manager can most easily reason about and adjust: A.

---

## 3. Golden Path

> **Per-category hybrid: Seasonal-naive-with-growth as the baseline, blended with a recent-trend EWMA, with a documented graceful degradation ladder — implemented in pure Java, every output overridable.**

### 3.1 The formula
For a target future period *p* (a specific week or month) and category *c*:

```
seasonal(c,p)   = actual(c, same-period-last-year)          // implicit seasonality
growth(c)       = clamp( recent 3–6 month YoY ratio, [0.5, 2.0] )   // bounded to prevent blow-ups
seasonalTerm    = seasonal(c,p) * growth(c)

trendTerm(c,p)  = EWMA(recent N periods of actual(c)) extrapolated to p   // Holt-style level+trend

baseline(c,p)   = w * seasonalTerm + (1 - w) * trendTerm
```

- `w` (seasonal weight) defaults high (e.g. **0.7**) because same-period-last-year is our strongest signal, but is a single tunable constant.
- **Degradation ladder** (checked in order, first that applies):
  1. Prior-year period exists → full hybrid above.
  2. No prior-year but ≥3 recent periods → **trend-only** (EWMA), `w=0`.
  3. <3 periods of history → **flat average** of what exists.
  4. No history at all → **0** (and surfaced to the user as "no basis — enter manually").
- **Growth clamped** to [0.5, 2.0] so a division by a tiny prior-year number can't produce an absurd forecast.
- **Override layer** sits on top: `final(c,p) = override(c,p) ?? baseline(c,p)`. Overrides are stored separately and merged at read time (never overwrite the computed baseline — so recomputation stays honest and the user can always "reset to algorithm").

### 3.2 Why this is the right call (not a cop-out)
- It is the **only** candidate that is *honest* about having 1.5 cycles: it uses the one real prior-year observation directly instead of pretending to estimate a seasonal curve from it.
- It directly implements the ticket's own stated intent: *"previous year period differences, growth rates, and seasonality."*
- It is **fully explainable**, which is a hard requirement given mandatory overrides.
- It adds **zero new dependencies and zero new services** — critical for a VM that has already OOM'd twice. All math is ~100 lines of `BigDecimal` over a per-category monthly/weekly series we already know how to build (reuse `CashFlowService`'s aggregation).
- It **degrades gracefully** on new/sparse categories instead of throwing or hallucinating.

### 3.3 What we deliberately are *not* doing (and when to revisit)
- **No Prophet/SARIMA/ML now.** Revisit once we have **≥3 full years** (≈2028), which is when seasonal estimation becomes statistically defensible. The engine is structured so the baseline function is swappable — a future `ProphetForecaster` could slot behind the same interface without touching the override layer or UI.
- **No confidence intervals in v1.** With 1.5 cycles, an interval would be pseudo-precision. We instead expose the *basis* of each number ("Feb 2025 × 1.12") so the user judges reliability themselves. Simple dispersion bands can be added later.

---

## 4. Summary
The impressive-sounding options (Prophet, SARIMA, ML) are **wrong for this dataset**, not because they're bad, but because 1.5 seasonal cycles cannot feed them honestly, and because a Python sidecar is disproportionate risk for a small file-based tool. The Golden Path — **seasonal-naive × growth blended with EWMA trend, per category, pure-Java, fully overridable** — scores highest on every axis that matters here, ships without new infrastructure, and is the most defensible number to put in front of management precisely because they can see how it was derived and correct it.
