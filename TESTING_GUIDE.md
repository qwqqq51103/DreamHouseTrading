# DreamHouseTrading Testing Guide

Last updated: 2026-06-03

This guide defines which commands and tests are required for Codex changes. Passing compile is only a syntax check. Passing unit tests is not coverage. Coverage is only valid when JaCoCo actually runs.

## 1. Commands

Quick compile:

```bash
mvn -q -DskipTests compile
```

Quick unit test without JaCoCo:

```bash
cmd /c "mvn -q -Djacoco.skip=true test"
```

Coverage test:

```bash
cmd /c "mvn -q test"
```

Full clean coverage report:

```bash
cmd /c "mvn -q clean test jacoco:report"
```

Rules:

- Compile passing does not mean the feature works.
- `-Djacoco.skip=true` confirms unit tests only; it does not confirm coverage.
- If JaCoCo cannot run locally because of file permission issues, report that explicitly and list the residual risk.
- Do not add low-value tests only to increase coverage.
- Do not add testing-only production APIs.

## 2. Required Test Level By Change Type

Docs only:
- Run compile if practical.
- No coverage required unless production behavior changed.

UI text only:
- Run compile.
- Manual UI verification is preferred when visible behavior changed.

UI settings:
- Run compile and unit tests.
- Test config load/save, template apply, custom template round-trip, and scanner request construction when behavior changes.

Config / template:
- Run compile and unit tests.
- Test built-in A/B/C protection, custom save/load, setting summary, replay request, and auto-monitor request.

Scanner:
- Run compile, unit tests, and coverage when possible.
- Test allowed and blocked cases through `MarketScannerService.scan(...)`, not only getters.

Decision / risk:
- Run compile, unit tests, and coverage when possible.
- Test allow path, reject path, reason, quantity, cash, cooldown, time window, and circuit-break rules.

Execution:
- Run compile, unit tests, and coverage when possible.
- Test `ExecutionEngine.executeDecision(...)`, `DecisionSource`, auto-managed flag, long-only behavior, order result, and paper trade record.

Backtest / replay:
- Run compile, unit tests, and coverage when possible.
- Test N+1 open, next tick execution, 13:25 force close, cost model, global position limits, daily trade limits, and same M5 bar rule.

CSV / report:
- Run compile and unit tests.
- Test UTF-8 with BOM, schema fields, gross/commission/tax/slippage/net, reasons, `DecisionSource`, auto-managed flag, and score components.

MarketDataCollector / RealtimeBarBuilder:
- Run compile, unit tests, and coverage when possible.
- Test source mode, incomplete ticks, no FinMind intraday fallback, volume delta conversion, and Taiwan M5 54-bar behavior.

SQL repository / schema:
- Run compile and focused repository tests.
- Avoid broad automatic schema changes without human review.

## 3. P0 / P1 / P2 Test Priorities

P0:
- `RadarReplayBacktestService`
- `BacktestEngine`
- `RiskManager`
- `ExecutionEngine.executeDecision(...)`
- `ReportGenerator` / `BacktestResult` / `TradeStatisticsAnalyzer`
- `PaperTradeRecorder`
- `AutoMonitorExecutionGate`

P1:
- `MarketDataCollectorFeed`
- `RealtimeBarBuilder`
- `TimeframeAggregator`
- SQL ticks / candlesticks source modes

P2:
- `RadarStrategyConfig`
- `SignalMonitorConfig`
- `SignalMonitorTemplateManager`
- A/B/C built-in templates
- Custom template round-trip
- UI setting summaries

## 4. Trading Semantics Tests

Prefer tests named by behavior, for example:

```text
shouldOpenLongWhenAllDayTradeConditionsPass
shouldBlockOpenLongWhenRsiIsShort
shouldBlockOpenLongAfter1325
shouldUseNextBarOpenToAvoidLookaheadBias
shouldExportBlockReasonToReplayCsv
```

Required cases when affected:

- N+1 open avoids lookahead bias.
- If the same K bar touches take profit and stop loss, stop loss wins.
- 13:25 blocks new `OPEN_LONG`.
- 13:25 closes auto-managed positions only.
- Manual positions are not incorrectly closed by auto-managed cutoff logic.
- v1 remains long-only.
- FinMind is not used as intraday realtime fallback.
- Replay with ticks slices by replay time.
- `TICKS_AGGREGATED` does not pre-aggregate future bars.
- Day trade quantity remains 1000 shares.
- Cash shortage rejects the order instead of reducing quantity.
- Stop-loss and post-exit cooldowns work.
- Daily max trades and max open positions work.
- Same M5 bar only allows one entry.
- CSV expected for Excel includes UTF-8 BOM.

## 5. Low-Value Tests To Avoid

Do not prioritize:

- Getter/setter-only tests.
- Constructor-only tests.
- Enum value tests.
- Null-only tests with no behavior.
- Swing constructor tests that do not verify behavior.
- Tests that require new production APIs used only by tests.

## 6. Verification Report Template

```text
Test coverage assessment:
- Changed behavior:
- Required tests:
- Tests added:
- Production classes touched:
- Commands run:
- Manual checks:
- Untested areas:
- Coverage status:
```
