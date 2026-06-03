# DreamHouseTrading Risk Areas

Last updated: 2026-06-03

This document marks areas where Codex must not make broad automatic changes. These modules contain trading semantics, data source boundaries, report schemas, or local runtime state.

## 1. General Rules

- Do not add real broker execution.
- Do not add executable short-selling behavior.
- Do not bypass `DecisionResult`.
- Do not bypass `RiskManager`.
- Do not bypass `ExecutionEngine.executeDecision(...)`.
- Do not use UI state as the source of core trading truth.
- Do not use FinMind as an intraday realtime fallback.
- Do not commit `logs/`, `data/`, `config/ui-layout.xml`, or paper trade CSV.
- Do not report `-Djacoco.skip=true test` as coverage.

## 2. High-Risk Areas

| Area | Risk | Codex Allowed | Required Limits |
|---|---|---|---|
| `src/main/java/com/dreamhouse/trading/core/execution` | Order semantics, paper execution, long-only boundary | Yes, with strict review | Must preserve `DecisionResult`, `RiskManager`, long-only v1, paper record fields, and tests. |
| `src/main/java/com/dreamhouse/trading/core/decision/risk` | Cash, quantity, time gates, cooldowns, circuit breakers | Yes, with strict review | Must test allow and reject cases and sync replay/backtest/auto-monitor semantics. |
| `src/main/java/com/dreamhouse/trading/core/decision` | Decision output and strategy voting | Yes, with review | Must preserve `DecisionResult` as standard output and avoid executable short path. |
| `src/main/java/com/dreamhouse/trading/core/scanner` | Entry signals, hard blocks, score components | Yes, with review | Must update reasons, score components, CSV/report visibility, and scanner tests. |
| `src/main/java/com/dreamhouse/trading/core/backtest` | Lookahead, fill model, costs, replay parity | Yes, with strict review | Must test N+1 open/next tick, stop-loss-first, costs, and global trade constraints. |
| `RadarReplayBacktestService` | Replay timing and execution parity | Yes, with strict review | Must avoid future bars and preserve global pacing/position limits. |
| `BacktestEngine` | Portfolio and fill behavior | Yes, with review | Must preserve cost model and trade pairing semantics. |
| `MarketDataCollectorFeed` | Intraday source boundary and source modes | Yes, with strict review | Must not call FinMind for intraday realtime fallback. Must expose source diagnostics. |
| `MarketDataCollectorRepository` | SQL reads/writes and schema creation | Limited | Broad schema changes require human review. Avoid hidden migrations. |
| `RealtimeBarBuilder` | Tick aggregation and volume correctness | Yes, with strict review | Must convert cumulative volume to delta and prevent 13:30 extra M5 bar. |
| `TimeframeAggregator` | M1/M5/D1/W1 aggregation | Yes, with review | Must preserve Taiwan M5 09:00-13:25 54-bar rule. |
| `PaperTradeRecorder` | Official paper trade CSV | Yes, with review | Must preserve BOM, `DecisionSource`, auto-managed flag, reasons, mode, quantity, and PnL. |
| `LogExporter` / reports | CSV/report schema | Yes, with review | Schema changes need tests and docs. |
| `MainFrameWithDocking.java` | Large UI class with workflow entry points | Only narrow changes | Do not reimplement core trading semantics in UI. Prefer core services for behavior. |
| Config/template classes | Strategy settings persistence | Yes, with review | Must preserve A/B/C built-ins and custom template round-trip. |
| `logs/`, `data/`, `config/ui-layout.xml` | Local runtime output | No | Never include in PR. |

## 3. Strictly Forbidden Automatic Changes

- Adding real broker order submission.
- Adding live short-selling execution.
- Making UI the source of risk or execution truth.
- Replacing core risk checks with UI checks.
- Replacing `DecisionResult` with ad hoc signal strings.
- Adding FinMind intraday realtime fallback.
- Treating `TaiwanStockKBar` as a realtime intraday source before the documented boundary.
- Writing local runtime outputs into source-controlled changes.
- Adding production APIs used only by tests.

## 4. Required Review Before Merge

Human/GPT review is required when a change touches:

- Any file in `core/execution`.
- Any file in `core/decision/risk`.
- `MarketScannerService`.
- `RadarReplayBacktestService`.
- `BacktestEngine`.
- `MarketDataCollectorFeed`.
- `MarketDataCollectorRepository`.
- `RealtimeBarBuilder`.
- `PaperTradeRecorder`.
- `LogExporter`.
- `MainFrameWithDocking.java`.
- Any CSV/report schema.
- Any config/template field that changes scanner behavior.

## 5. Dirty Worktree Policy

Before editing:

- Run `git status --short --branch`.
- Report existing modified and untracked files.
- Do not revert unrelated user changes.
- Do not mix unrelated dirty files into the patch.

Before PR:

- Confirm no local `logs/`, `data/`, `config/ui-layout.xml`, diagnostic CSV, or paper trade CSV is included.
- If already tracked local output exists, report it and require human decision before removal or history cleanup.

## 6. High-Risk Change Report Template

```text
High-risk change:
- Area:
- Reason:
- Expected behavior:
- Affected chain:
- Trading semantics changed:
- Backtest/replay changed:
- Auto-monitor changed:
- CSV/report changed:
- Data source changed:
- Tests:
- Residual risk:
- Needs human review:
```
