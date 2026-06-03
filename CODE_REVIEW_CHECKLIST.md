# DreamHouseTrading Code Review Checklist

Last updated: 2026-06-03

Use this checklist when GPT or a human reviewer evaluates a Codex diff or PR. Findings should focus on bugs, regressions, missing tests, and unclear trading semantics.

## 1. Review Inputs

Before review, confirm:

- Codex reported the dirty worktree state.
- Codex listed changed files and intended scope.
- Codex listed feature-chain impact.
- Codex listed tests and coverage status.
- Diff does not contain local runtime output.

## 2. Feature Chain

Check whether the change affects:

- UI.
- Config / template.
- Scanner.
- `MarketScanResult`.
- `DecisionResult`.
- `RiskManager`.
- `ExecutionEngine`.
- Backtest / replay.
- CSV / report.
- Paper trade record.
- Docs.

If any affected layer is missing, request changes.

## 3. Trading Semantics

Check:

- `DecisionResult` remains the standard decision output.
- Entry checks pass through `RiskManager` or an equivalent core risk service.
- Executable decisions use `ExecutionEngine.executeDecision(...)`.
- UI does not reimplement trading, risk, or order semantics.
- No new executable short-selling path exists.
- v1 remains long-only.
- `DAY_TRADE` quantity remains one lot / 1000 shares.
- Cash shortage rejects instead of auto-reducing quantity.
- `13:25` cutoff blocks new auto `OPEN_LONG`.
- `13:25` force close applies to auto-managed positions and not manual positions.
- Stop-loss cooldown, post-exit cooldown, daily loss halt, losing-streak halt, daily max trades, max open positions, and same M5 bar rules still work.
- Machine decisions do not depend on fragile `reason.contains(...)` text.

## 4. Lookahead Bias

Check:

- Signal from completed K bar N executes at N+1 open or a next visible tick.
- Replay at a time such as 09:30:20 cannot see the full 09:30-09:34:59 M5 bar.
- Tick aggregation slices by replay time.
- Candles-only mode uses only completed bars.
- Cross-day warmup initializes indicators but does not become trading performance.
- Batch backtest does not sum independent single-symbol results without global position and pacing limits.

## 5. Data Source Boundaries

Check:

- Intraday radar uses local SQL / `MarketDataCollectorFeed`.
- No FinMind intraday realtime fallback was added.
- `TaiwanStockKBar` is not used as a realtime intraday K-bar source before the documented boundary.
- Stale guard prevents bad scans; it is not a UI clearing mechanism.
- `TICKS_AGGREGATED`, `CANDLES_ONLY`, and `AUTO` behavior is explicit and reportable.
- Incomplete tick aggregation reports incomplete source status.
- SQL/snapshot cumulative volume is converted to delta before updating live bars.

## 6. CSV / Report

Check:

- CSV intended for Excel uses UTF-8 with BOM.
- Header/schema changes are documented.
- Existing schema fields are not silently removed.
- Reports preserve grossProfit, commission, tax, slippageCost, and netProfit.
- Reports preserve entry reason, exit reason, block reason, and radar score components.
- Paper records preserve `DecisionSource`, auto-managed flag, trade mode, timeframe, quantity, PnL, and strategy summary.
- Blocked candidates do not collapse into generic `No entry signal`.

## 7. Tests

Check:

- Tests cover both allowed and blocked behavior.
- Tests exercise real flow where possible, not just getters.
- Scanner tests call `MarketScannerService.scan(...)`.
- Replay/backtest tests prove lookahead protection.
- Execution tests prove `DecisionResult` and risk path usage.
- CSV tests verify fields and BOM where relevant.
- Codex did not add testing-only production APIs.
- `-Djacoco.skip=true test` was not reported as coverage.

## 8. Automatic Merge Blockers

Do not merge automatically if:

- Execution, risk, scanner, or backtest changed without focused tests.
- Trading semantics changed without docs.
- CSV/report changed without schema tests and docs.
- Coverage failed or was skipped without explanation.
- New short execution behavior appears.
- FinMind intraday fallback appears.
- Local `logs/`, `data/`, `config/ui-layout.xml`, or paper trade CSV is included.
- Dirty worktree contains unrelated files mixed into the PR.
- Reason text is used as machine-state logic.
- Replay/backtest lookahead behavior is untested.

## 9. Review Output Template

```text
Review result:
- Pass / fail / request changes:

Blocking findings:
- ...

Non-blocking findings:
- ...

Missing verification:
- ...

Risk assessment:
- ...

Suggested next action:
- ...
```
