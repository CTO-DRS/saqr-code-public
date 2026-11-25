# الموجّه الذكي · Saqr Smart Router (v0.17.0)

> امتداد لموجّه الصقر (Falcon Router, v0.13.2) لا بديل عنه. خلف مفتاح `smartRouter` **المُطفأ افتراضياً**: حين يكون مطفأً يعمل موجّه الصقر كما كان تماماً.
>
> An extension of Falcon Router (v0.13.2), not a replacement. Behind the `smartRouter` switch, **off by default**: when off, Falcon Router behaves exactly as before (cheapest capable model of the active provider for planner / review / compaction when the price table proves a saving; the coder never routed).

Code: `agent/ModelRouter.kt` (`ModelCatalog`, `SmartRouter`, `Failover`, `SpendCap`, `CostEstimate`), `SettingsStore.routableProviders()`, wired into `AgentSession` (`smartRoute`, `clientFor`, `completeStep` failover, spend caps, Chief estimate). Tests: `V017FeaturesTest`, `V017SettingsTest`.

## 1. جدول القدرات · Capability table

For the models the app already ships (`Providers.suggestedModels`): tool calling, context window and relative speed (1 slow · 2 normal · 3 fast — only a tie-breaker), from the vendors' model pages (late 2025). Vision comes from the existing `Vision` heuristic **and the user's per-model override**, so there is one source of truth. Price = input + output USD/1M from the user's price table (seeds + their edits); an unpriced model is never chosen "to save money". Unknown models (custom/OpenRouter ids outside the table) get conservative defaults: their provider's context window, tools on, no vision unless `Vision` says so. `deepseek-reasoner` is marked without tool calling (the conservative reading), so tool-using steps are never routed to it.

| Model | Tools | Context | Speed |
|---|---|---|---|
| claude-opus-4-1 · claude-sonnet-4-5 · claude-haiku-4-5 | ✓ | 200k | 1 · 2 · 3 |
| gpt-4.1 · gpt-4.1-mini | ✓ | 1,047,576 | 2 · 3 |
| gpt-4o · gpt-4o-mini | ✓ | 128k | 2 · 3 |
| o4-mini | ✓ | 200k | 1 |
| gemini-2.5-pro · gemini-2.5-flash | ✓ | 1,048,576 | 1 · 3 |
| deepseek-chat · deepseek-reasoner | ✓ · ✗ | 128k | 2 · 1 |
| qwen3-coder | ✓ | 262,144 | 2 |

## 2. التوفّر · Availability (BYOK)

Only providers the user can actually call: a saved API key, or a keyless custom endpoint with a real `http(s)://host` base URL **and** a model. The active provider is always included (it is the user's choice). No key is ever assumed; a provider without a key never appears in a route or a failover chain.

## 3. قواعد التوجيه · Routing rules

`SmartRouter.route(request)` with the role/stage tier, the user's coder model (primary), needed capabilities (vision when the turn carries images; tool calling when the step has tools) and the estimated prompt size (must fit with 25% headroom):

| Tier | Used by | Rule |
|---|---|---|
| CHEAP | planner, Chief classification, compaction, session summary, cheap roles (Research, Tester, Documentation, Deployment) | cheapest capable **priced** model that is cheaper than the coder (any configured provider; same provider wins ties) — else the coder |
| STRONG | the coder, Developer, Debugger, Architect, Optimizer | the user's coder model, unless it lacks a needed capability (images → strongest vision model; context too large → a model whose window fits) |
| INDEPENDENT_REVIEW | reviewer, Security | a capable model of tier ≥ MID **different from the coder**, preferring another provider/family, then cheaper; if none, the coder's model reviews and the reason says so |

Explicit picks still win: `reviewModel` for review roles, `plannerModel` for the Planner/Chief. With images and the router on, the composer no longer warns about a non-vision coder when a configured vision model exists — the turn is routed to it.

## 4. سلسلة البدائل · Failover chain

When a step fails with a transient error (429/408/5xx/timeout/network — the Phase 2 `LlmRetry` rules) and the one retry also fails: the user's Phase 2 **fallback model first** (if its provider is available), then capable models on **other providers** (an outage usually takes a whole provider down), closest tier first then cheaper, then other models of the same provider — up to 3. `Failover.run` tries each once; a non-transient error (401, 400, cancel) surfaces immediately, never "fails over" a bad key. Each switch posts the existing FALLBACK notice and an audit line. With `smartRouter` off, the Phase 2 single-fallback path runs unchanged.

## 5. الإنفاق · Spend

- **Pre-run estimate (Chief mode, `teamCostEstimate`):** with the router on, each subtask's model is priced with its real per-model price, starting from the current context size and growing ~1.5k tokens per step, shown as a range (half to double the typical steps). An unpriced model gives "unpriced", never a guess. Router off → the v0.16 estimate.
- **Spend caps:** `dailySpendCap` and `monthlySpendCap` (USD, 0 = off). Priced spend of today / this month from the usage ledger plus this run is checked before a run starts and before every step (and for the whole team in Chief mode). Reaching a cap stops the run cleanly with a clear notice («Stopped: today's spend cap is reached…»). Separate from the existing monthly budget (warn at 80%, confirm at 100%). Unpriced spend cannot be capped (same honesty rule as the v0.15 cost budget).
- **Per-agent cost** keeps the v0.16 per-role line.

## 6. السجل · Logging

Every decision is written to `.saqr/audit.jsonl` as tool `router` with `label → provider · model (reason)` (when the audit log is on). A decision that moves away from the user's coder model also posts one compact chat pill (deduplicated per run). Failover switches are audited with the failing error's first 80 chars (no secrets: the error text comes from the provider's HTTP status line).

## 7. الإعدادات · Settings

| Key | Default |
|---|---|
| `smartRouter` | off |
| `dailySpendCap` | 0 (off) |
| `monthlySpendCap` | 0 (off) |

## 8. المتبقي · Remaining

- The router and failover are unit-tested (including acceptance scenario 12 with a fake client) and wired into the real loop, but were not exercised against live providers on a device in this release.
- Capability rows are static for the shipped models; models fetched from a provider's model list use conservative defaults. Live capability discovery and model-performance-aware routing are not built.
