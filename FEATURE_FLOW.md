# DreamHouseTrading Feature Flow

Last updated: 2026-06-03

This document defines the feature chain Codex must inspect before changing DreamHouseTrading. A change is not complete just because it compiles. Every trading feature must be traced through the layers that can affect user behavior, scanner output, risk gates, execution simulation, replay, reports, and documentation.

## 1. Purpose

DreamHouseTrading is a Java 17 Swing trading analysis platform. It supports analysis, scanning, decision making, backtesting, replay, and paper-trade style execution simulation. It must not add real broker order routing.

The main risk in this project is partial implementation: changing one class while leaving UI, config, scanner, risk, replay, CSV, or docs inconsistent. This file is the checklist for avoiding that failure.

## 2. Canonical Feature Chain

For every new feature or behavior change, inspect the following chain:

1. UI / user entry point
2. Config / template / settings persistence
3. Scanner / signal generation
4. `MarketScanResult`
5. `DecisionResult`
6. `RiskManager` / risk gate
7. `ExecutionEngine.executeDecision(...)`
8. Position / order / trade record
9. Backtest / replay
10. CSV / report export
11. Paper trade record
12. `README.md` / `PROJECT_DOCUMENTATION.md` / `AGENTS.md`
13. Main application "About DreamHouseTrading" text
14. Main application "day-trading indicator settings" text

If a layer does not apply, the implementation report must state why.

## 3. Common Feature Types

Scanner condition changes:
- Check `RadarStrategyConfig`, `MarketScannerService`, `MarketScanResult`, replay `_symbols.csv`, backtest statistics, UI visible reasons, and docs.

Risk or execution changes:
- Check `DecisionResult`, `RiskManager`, `ExecutionEngine`, paper trade CSV, auto-managed flags, replay/backtest parity, and tests for both allow and block paths.

Data source changes:
- Check `MarketDataCollectorFeed`, `MarketDataCollectorRepository`, `RealtimeBarBuilder`, FinMind boundaries, stale diagnostics, UTF-8 BOM CSV output, replay source modes, and docs.

Report or CSV schema changes:
- Check exporters, paper trade record, backtest report dialogs, Excel BOM behavior, docs, and compatibility with existing CSV readers.

UI setting changes:
- Check load, save, built-in A/B/C templates, custom template round-trip, setting summary, scanner request construction, replay request construction, and tests.

## 4. Minimum Completion Rules

A feature can be considered complete only when:

- UI has an entry point, or the report clearly says it is a pure core feature.
- Config/template state is saved and can be rebuilt without losing behavior.
- Scanner, decision, risk, and execution semantics match.
- Replay, backtest, and auto monitor use the same core rules.
- CSV/report exports contain enough diagnostic fields.
- Paper trade records preserve `DecisionSource`, auto-managed flag, mode, quantity, PnL, entry reason, and exit reason.
- Tests cover at least one passing case and one blocking/failure case when trading semantics changed.
- Compile and required tests were run.
- Docs were updated when user behavior, data boundaries, strategy semantics, or report fields changed.

## 5. Codex Pre-Change Report

Before editing, Codex should report:

```text
Feature chain risk:
- Goal:
- Existing entry points:
- Planned files:
- New class / method / config / enum:
- Affected layers:
- Data source impact:
- Trading semantics impact:
- CSV / report impact:
- Backtest / replay impact:
- Paper trade impact:
- Planned tests:
- Files that must not be changed:
```

## 6. Codex Completion Report

After editing, Codex should report:

```text
Feature chain check:
- UI:
- Config / Template:
- Scanner:
- MarketScanResult:
- DecisionResult:
- RiskManager:
- ExecutionEngine:
- Backtest / Replay:
- CSV / Report:
- Paper Trade Record:
- Docs:

Leak prevention:
- New class / method call sites:
- New config save/load:
- New UI field applies to core config:
- New strategy condition appears in reasons/reports:
- Testing-only production API added:
- reason string used as machine logic:

Verification:
- compile:
- unit test:
- coverage:
- manual verification:
```

## 7. Acceptance Standard

Do not accept a change when:

- It changes core trading semantics without tests.
- It bypasses `RiskManager`, `DecisionResult`, or `ExecutionEngine.executeDecision(...)`.
- It adds executable short-selling behavior.
- It changes replay/backtest but does not address lookahead bias.
- It changes CSV/report fields without docs and export tests.
- It submits local runtime output from `logs/`, `data/`, `config/ui-layout.xml`, or paper trade CSV.
- It claims coverage passed when only `-Djacoco.skip=true` was run.
