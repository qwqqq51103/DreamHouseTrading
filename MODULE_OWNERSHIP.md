# DreamHouseTrading Module Ownership

Last updated: 2026-06-03

This file explains module responsibilities and modification rules for Codex. It is not a people ownership map; it is a semantic ownership map.

| Module / package | Responsibility | Main risks | Codex scope | Required checks |
|---|---|---|---|---|
| `src/main/java/com/dreamhouse/trading/ui` | Swing UI, docking panels, dialogs, user actions | UI may accidentally become trading source of truth | Narrow UI changes allowed | Do not reimplement risk/order semantics. Check config, scanner request, docs, and manual UI behavior. |
| `ui/MainFrameWithDocking.java` | Main application workflow and many integration entry points | Very large class; easy to mix UI and core behavior | Only small scoped edits | Prefer core services. Verify feature chain and avoid unrelated refactors. |
| `core/scanner` | Radar scanning, signal aggregation, hard blocks, score components | Entry logic, block reasons, score reporting | Allowed with review | Test through `MarketScannerService.scan(...)`; update CSV/report/docs when semantics change. |
| `core/decision` | Decision engine, classifier, voting, strategy signals | `DecisionResult` consistency and long-only boundary | Allowed with review | Preserve standardized decision output and avoid executable short path. |
| `core/decision/risk` | Risk config, risk gates, violations | Cash, time rules, cooldowns, circuit breakers | Strict review | Add allow and block tests. Sync auto-monitor, replay, and backtest. |
| `core/execution` | Paper execution, orders, execution results | Simulated orders can drift from decisions and risk | Strict review | Use `ExecutionEngine.executeDecision(...)`; preserve paper-trade fields and long-only v1. |
| `core/backtest` | Backtest engine, replay, reports, portfolio/trades | Lookahead, fill prices, costs, global limits | Strict review | Test N+1 open, next tick, stop-loss-first, costs, and replay parity. |
| `core/monitor` | Auto monitor config/service/templates/gates | Scheduler, template persistence, pacing, cutoff | Allowed with review | Test A/B/C protection, custom round-trip, `DecisionSource`, auto-managed flag, and cadence rules. |
| `core/logging` | Paper trade and CSV logging | CSV schema, Excel BOM, source traceability | Allowed with review | Verify BOM, fields, `DecisionSource`, auto-managed flag, mode, quantity, PnL, reasons. |
| `core/finmind` | FinMind API client/importers/boundaries | API quota, dataset permissions, intraday misuse | Allowed with review | Do not add intraday realtime fallback. Use fake gateway tests. |
| `core/cache` | Local H2 cache and watchlist persistence | Local state and cleanup behavior | Allowed | Avoid changing persistent semantics without tests. |
| `core/MarketDataCollectorFeed.java` | SQL intraday feed and session bar loading | Source-mode drift and stale data | Strict review | Test `TICKS_AGGREGATED`, `CANDLES_ONLY`, `AUTO`, incomplete ticks, and no FinMind fallback. |
| `core/MarketDataCollectorRepository.java` | SQL access and schema helper behavior | Hidden migrations and SQL compatibility | Limited | Broad schema changes require human review. |
| `core/RealtimeBarBuilder.java` | Live tick to K-bar aggregation | Volume explosion and extra 13:30 M5 bar | Strict review | Test cumulative volume to delta and Taiwan M5 54-bar behavior. |
| `core/TimeframeAggregator.java` | Timeframe aggregation | M1/M5/D1/W1 consistency | Allowed with review | Test timeframe boundaries and session rules. |
| `src/test/java` | Regression protection | Low-value or flaky tests | Allowed | Prefer behavior tests. Avoid testing-only production API. |
| `README.md` | User setup and usage | User-facing behavior drift | Allowed | Update when usage, data boundary, or workflow changes. |
| `PROJECT_DOCUMENTATION.md` | Architecture, progress, constraints | Architectural drift | Allowed | Update when strategy, replay, report, or limitations change. |
| `AGENTS.md` | AI collaboration rules | Process drift | Allowed with care | Do not replace wholesale. Add focused sections only. |

## Test Ownership By Module

- Scanner behavior: `MarketScannerServiceTest`, template/settings tests.
- Risk behavior: `RiskManagerTest`, auto-monitor gate tests.
- Execution behavior: `ExecutionEngineTest`.
- Replay/backtest behavior: `RadarReplayBacktestServiceTest`, `BacktestEngineTest`, report/statistics tests.
- Data source behavior: `MarketDataCollectorFeedTest`, `RealtimeBarBuilderTest`, FinMind importer/client tests.
- Logging/report behavior: `PaperTradeRecorderTest`, `LogExporterTest`, `BacktestReportExporterTest`.

## Documentation Ownership

When behavior changes, update:

- User operation or data source boundary: `README.md`.
- Architecture, strategy flow, limitations, validation: `PROJECT_DOCUMENTATION.md`.
- AI collaboration rules: `AGENTS.md`.
- Feature-chain rules: `FEATURE_FLOW.md`.
- Testing requirements: `TESTING_GUIDE.md`.
- Review rules: `CODE_REVIEW_CHECKLIST.md`.
- High-risk areas: `RISK_AREAS.md`.
