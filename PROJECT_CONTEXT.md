# DreamHouseTrading Project Context

Last updated: 2026-06-03

DreamHouseTrading is a Java 17 / Maven / Swing desktop trading analysis platform. It is designed for analysis, market scanning, decision support, backtesting, replay, and simulated execution. It must not perform real broker order placement.

## 1. Technology

- Language: Java 17
- Build: Maven
- UI: Swing, FlatLaf, JFreeChart, Modern Docking
- Tests: JUnit 5, Mockito, AssertJ, Surefire, JaCoCo
- Primary local market data: MySQL `market_data`

## 2. Product Boundary

The system may:

- Load market data.
- Generate signals.
- Build standardized decisions.
- Apply risk checks.
- Simulate execution.
- Backtest and replay.
- Export reports and paper-trade records.

The system must not:

- Route real broker orders.
- Add executable short-selling paths without explicit future scope approval.
- Use UI state as the source of core trading truth.
- Use FinMind as a realtime intraday fallback source.

## 3. Core Flow

1. Market Data
2. Signal Generation
3. Decision & Risk
4. Execution Simulation
5. Backtest & Reporting

Important core outputs and services:

- `MarketScanResult`
- `DecisionResult`
- `RiskManager`
- `ExecutionEngine`
- `PaperTradeRecorder`

## 4. Data Source Boundary

Intraday radar and intraday K bars:
- Prefer `MarketDataCollectorFeed` reading local SQL `market_data`.
- Do not fallback to FinMind realtime quotes.

FinMind:
- Low-frequency data.
- After-hours data.
- News.
- Broker/branch data.
- Manual API query workflows.

Yahoo / other feeds:
- Secondary or fallback market-data workflows only where documented.

## 5. Trading Rules

Current v1 day-trade automation is long-only.

Key rules:

- Auto monitor and today's opportunity radar are forced to `DAY_TRADE`.
- Auto `DAY_TRADE` order quantity is fixed at 1000 shares.
- Insufficient cash rejects; it must not reduce to odd-lot quantity.
- After 13:25, no auto-monitor `OPEN_LONG`.
- At/after 13:25, auto-managed positions are force-closed.
- Manual positions must not be treated as auto-managed.
- RSI oversold is not by itself a long-entry reason.
- `SignalRSI = SHORT` blocks auto-monitor `OPEN_LONG`.
- Close <= VWAP, VWAP slope <= 0, failed Volume Sustain, and ATR chase excess are hard block candidates or explicit block reasons.

## 6. Backtest / Replay Rules

- Avoid lookahead bias.
- Signal on completed K bar N can execute only on N+1 open or a next visible tick.
- If take profit and stop loss are both touched in the same K bar, count stop loss first.
- Replay with ticks must aggregate only ticks visible at replay time.
- Candles-only mode can use only completed bars.
- Cross-day warmup can initialize indicators but must not pollute selected-date performance.
- Backtest/replay reports must preserve timeframe and source diagnostics.

## 7. AI Collaboration Mode

Recommended mode: semi-automatic GPT + Codex workflow.

- GPT: requirement decomposition, spec, testing strategy, code review.
- Codex: repo reading, code/doc/test edits, diff or PR preparation.
- User: final confirmation, merge, and production judgment.

This project is not ready for highly automated merging because trading semantics are dense, UI and core workflows still intersect, and regression risk requires human review.

## 8. Local State

Do not commit:

- `logs/`
- `data/`
- `config/ui-layout.xml`
- diagnostic CSV
- paper trade CSV
- local credentials such as `datasource.properties`

If these files are already tracked, report them before making PR decisions.
