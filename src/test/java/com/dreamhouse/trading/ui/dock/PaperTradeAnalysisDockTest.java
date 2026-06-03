package com.dreamhouse.trading.ui.dock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PaperTradeAnalysisDockTest {

    @TempDir
    Path tempDir;

    @Test
    void importsPaperTradeCsvFilesIntoLifecycleJourneys() throws Exception {
        Files.writeString(tempDir.resolve("orders_20260513.csv"), """
                \uFEFFevent_time,source,order_id,position_id,symbol,side,status,order_status,quantity,requested_price,executed_price,stop_loss,take_profit,realized_pnl,commission,decision_action,trade_mode,confidence,risk_reward,reason,message
                2026-05-13 09:15:00,auto-monitor,O1,P1,2330.TW,BUY,SUCCESS,FILLED,1000,100.0,100.0,99.0,103.0,0,142.5,OPEN_LONG,DAY_TRADE,0.8,2.0,entry,filled
                2026-05-13 09:25:00,auto-stop,O2,P1,2330.TW,SELL,SUCCESS,FILLED,1000,100.0,99.0,99.0,103.0,-1200,141.0,CLOSE_POSITION,DAY_TRADE,0.0,,stop,filled
                """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("trade_setups_20260513.csv"), """
                \uFEFFentry_time,source,position_id,order_id,symbol,trade_mode,quantity,entry_price,stop_loss,take_profit,setup_score,confidence,risk_reward,decision_action,decision_source,reason,raw_signal_summary,block_reason
                2026-05-13 09:15:00,auto-monitor,P1,O1,2330.TW,DAY_TRADE,1000,100.0,99.0,103.0,0.75,0.8,2.0,OPEN_LONG,TECHNICAL,entry,RSI|EMA,
                """, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("completed_trades_20260513.csv"), """
                \uFEFFclosed_time,source,position_id,symbol,trade_mode,entry_time,exit_time,holding_minutes,quantity,entry_price,exit_price,stop_loss,take_profit,is_win,outcome,gross_pnl,net_pnl,return_pct,mae,mfe,mae_pct,mfe_pct,setup_score,setup_confidence,setup_risk_reward,entry_order_id,exit_order_id,entry_reason,exit_reason,raw_signal_summary,block_reason
                2026-05-13 09:25:01,auto-stop,P1,2330.TW,DAY_TRADE,2026-05-13 09:15:00,2026-05-13 09:25:00,10,1000,100.0,99.0,99.0,103.0,false,LOSS,-1000,-1200,-1.0,-1000,500,-1.0,0.5,0.75,0.8,2.0,O1,O2,entry,stop,RSI|EMA,
                """, StandardCharsets.UTF_8);

        PaperTradeAnalysisDock dock = new PaperTradeAnalysisDock();
        PaperTradeAnalysisDock.ImportResult result = dock.importLogs(tempDir, "2026-05-13");

        assertThat(result.orders().rows()).hasSize(2);
        assertThat(result.setups().rows()).hasSize(1);
        assertThat(result.completed().rows()).hasSize(1);
        assertThat(result.journeys()).hasSize(1);
        assertThat(result.journeys().get(0).status()).isEqualTo("已平倉");
    }
}
