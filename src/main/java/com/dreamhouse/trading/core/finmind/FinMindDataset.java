package com.dreamhouse.trading.core.finmind;

public enum FinMindDataset {
    TAIWAN_STOCK_INFO("TaiwanStockInfo", Tier.FREE, false, false, false),
    TAIWAN_STOCK_INFO_WITH_WARRANT("TaiwanStockInfoWithWarrant", Tier.FREE, false, false, false),
    TAIWAN_STOCK_INFO_WITH_WARRANT_SUMMARY("TaiwanStockInfoWithWarrantSummary", Tier.SPONSOR, true, true, false),
    TAIWAN_STOCK_TRADING_DATE("TaiwanStockTradingDate", Tier.FREE, false, false, false),
    TAIWAN_STOCK_PRICE("TaiwanStockPrice", Tier.FREE, true, false, true),
    TAIWAN_STOCK_PRICE_ADJ("TaiwanStockPriceAdj", Tier.FREE, true, false, true),
    TAIWAN_STOCK_PRICE_TICK("TaiwanStockPriceTick", Tier.BACKER, true, true, false),
    TAIWAN_STOCK_PER("TaiwanStockPER", Tier.FREE, true, false, false),
    TAIWAN_STOCK_STATISTICS_OF_ORDER_BOOK_AND_TRADE("TaiwanStockStatisticsOfOrderBookAndTrade", Tier.FREE, false, true, false),
    TAIWAN_VARIOUS_INDICATORS_5_SECONDS("TaiwanVariousIndicators5Seconds", Tier.FREE, false, true, false),
    TAIWAN_STOCK_DAY_TRADING("TaiwanStockDayTrading", Tier.FREE, true, false, true),
    TAIWAN_STOCK_TOTAL_RETURN_INDEX("TaiwanStockTotalReturnIndex", Tier.FREE, true, false, false),
    TAIWAN_STOCK_10_YEAR("TaiwanStock10Year", Tier.BACKER, true, false, true),
    TAIWAN_STOCK_K_BAR("TaiwanStockKBar", Tier.SPONSOR, true, true, false),
    TAIWAN_STOCK_WEEK_PRICE("TaiwanStockWeekPrice", Tier.BACKER, true, false, true),
    TAIWAN_STOCK_MONTH_PRICE("TaiwanStockMonthPrice", Tier.BACKER, true, false, true),
    TAIWAN_STOCK_EVERY_5_SECONDS_INDEX("TaiwanStockEvery5SecondsIndex", Tier.BACKER, false, true, false),
    TAIWAN_STOCK_SUSPENDED("TaiwanStockSuspended", Tier.BACKER, false, false, false),
    TAIWAN_STOCK_DAY_TRADING_SUSPENSION("TaiwanStockDayTradingSuspension", Tier.BACKER, false, false, false),
    TAIWAN_STOCK_PRICE_LIMIT("TaiwanStockPriceLimit", Tier.FREE, true, false, true),

    TAIWAN_STOCK_MARGIN_PURCHASE_SHORT_SALE("TaiwanStockMarginPurchaseShortSale", Tier.FREE, true, false, true),
    TAIWAN_STOCK_TOTAL_MARGIN_PURCHASE_SHORT_SALE("TaiwanStockTotalMarginPurchaseShortSale", Tier.FREE, false, false, false),
    TAIWAN_STOCK_INSTITUTIONAL_INVESTORS_BUY_SELL("TaiwanStockInstitutionalInvestorsBuySell", Tier.FREE, true, false, true),
    TAIWAN_STOCK_TOTAL_INSTITUTIONAL_INVESTORS("TaiwanStockTotalInstitutionalInvestors", Tier.FREE, false, false, false),
    TAIWAN_STOCK_SHAREHOLDING("TaiwanStockShareholding", Tier.FREE, true, false, true),
    TAIWAN_STOCK_HOLDING_SHARES_PER("TaiwanStockHoldingSharesPer", Tier.BACKER, true, false, true),
    TAIWAN_STOCK_SECURITIES_LENDING("TaiwanStockSecuritiesLending", Tier.FREE, true, false, true),
    TAIWAN_STOCK_MARGIN_SHORT_SALE_SUSPENSION("TaiwanStockMarginShortSaleSuspension", Tier.FREE, true, false, true),
    TAIWAN_DAILY_SHORT_SALE_BALANCES("TaiwanDailyShortSaleBalances", Tier.FREE, true, false, true),
    TAIWAN_SECURITIES_TRADER_INFO("TaiwanSecuritiesTraderInfo", Tier.FREE, false, false, false),
    TAIWAN_STOCK_TRADING_DAILY_REPORT("TaiwanStockTradingDailyReport", Tier.SPONSOR, true, true, false),
    TAIWAN_STOCK_WARRANT_TRADING_DAILY_REPORT("TaiwanStockWarrantTradingDailyReport", Tier.SPONSOR, true, true, false),
    TAIWAN_STOCK_GOVERNMENT_BANK_BUY_SELL("TaiwanstockGovernmentBankBuySell", Tier.SPONSOR, false, true, false),
    TAIWAN_TOTAL_EXCHANGE_MARGIN_MAINTENANCE("TaiwanTotalExchangeMarginMaintenance", Tier.BACKER, false, false, false),
    TAIWAN_STOCK_TRADING_DAILY_REPORT_SEC_ID_AGG("TaiwanStockTradingDailyReportSecIdAgg", Tier.SPONSOR, true, false, false),
    TAIWAN_STOCK_BLOCK_TRADING_DAILY_REPORT("TaiwanStockBlockTradingDailyReport", Tier.SPONSOR, false, true, false),
    TAIWAN_STOCK_BLOCK_TRADE("TaiwanStockBlockTrade", Tier.SPONSOR, true, false, true),
    TAIWAN_STOCK_LOAN_COLLATERAL_BALANCE("TaiwanStockLoanCollateralBalance", Tier.SPONSOR, true, false, true),
    TAIWAN_STOCK_DISPOSITION_SECURITIES_PERIOD("TaiwanStockDispositionSecuritiesPeriod", Tier.BACKER, true, false, true),

    TAIWAN_STOCK_FINANCIAL_STATEMENTS("TaiwanStockFinancialStatements", Tier.FREE, true, false, true),
    TAIWAN_STOCK_BALANCE_SHEET("TaiwanStockBalanceSheet", Tier.FREE, true, false, true),
    TAIWAN_STOCK_CASH_FLOWS_STATEMENT("TaiwanStockCashFlowsStatement", Tier.FREE, true, false, true),
    TAIWAN_STOCK_DIVIDEND("TaiwanStockDividend", Tier.FREE, true, false, true),
    TAIWAN_STOCK_DIVIDEND_RESULT("TaiwanStockDividendResult", Tier.FREE, true, false, true),
    TAIWAN_STOCK_MONTH_REVENUE("TaiwanStockMonthRevenue", Tier.FREE, true, false, true),
    TAIWAN_STOCK_CAPITAL_REDUCTION_REFERENCE_PRICE("TaiwanStockCapitalReductionReferencePrice", Tier.FREE, true, false, false),
    TAIWAN_STOCK_MARKET_VALUE("TaiwanStockMarketValue", Tier.BACKER, true, false, true),
    TAIWAN_STOCK_DELISTING("TaiwanStockDelisting", Tier.FREE, false, false, false),
    TAIWAN_STOCK_MARKET_VALUE_WEIGHT("TaiwanStockMarketValueWeight", Tier.BACKER, true, false, true),
    TAIWAN_STOCK_SPLIT_PRICE("TaiwanStockSplitPrice", Tier.FREE, false, false, false),
    TAIWAN_STOCK_PAR_VALUE_CHANGE("TaiwanStockParValueChange", Tier.FREE, false, false, false),

    TAIWAN_FUT_OPT_DAILY_INFO("TaiwanFutOptDailyInfo", Tier.FREE, false, false, false),
    TAIWAN_FUTURES_DAILY("TaiwanFuturesDaily", Tier.FREE, true, false, true),
    TAIWAN_OPTION_DAILY("TaiwanOptionDaily", Tier.FREE, true, false, true),
    TAIWAN_FUTURES_TICK("TaiwanFuturesTick", Tier.BACKER, true, true, false),
    TAIWAN_OPTION_TICK("TaiwanOptionTIck", Tier.BACKER, true, true, false),
    TAIWAN_FUTURES_INSTITUTIONAL_INVESTORS("TaiwanFuturesInstitutionalInvestors", Tier.FREE, true, false, true),
    TAIWAN_OPTION_INSTITUTIONAL_INVESTORS("TaiwanOptionInstitutionalInvestors", Tier.FREE, true, false, true),
    TAIWAN_FUTURES_INSTITUTIONAL_INVESTORS_AFTER_HOURS("TaiwanFuturesInstitutionalInvestorsAfterHours", Tier.BACKER, true, false, true),
    TAIWAN_OPTION_INSTITUTIONAL_INVESTORS_AFTER_HOURS("TaiwanOptionInstitutionalInvestorsAfterHours", Tier.BACKER, true, false, true),
    TAIWAN_FUTURES_DEALER_TRADING_VOLUME_DAILY("TaiwanFuturesDealerTradingVolumeDaily", Tier.FREE, true, false, true),
    TAIWAN_OPTION_DEALER_TRADING_VOLUME_DAILY("TaiwanOptionDealerTradingVolumeDaily", Tier.FREE, true, false, true),
    TAIWAN_FUTURES_OPEN_INTEREST_LARGE_TRADERS("TaiwanFuturesOpenInterestLargeTraders", Tier.BACKER, true, false, true),
    TAIWAN_OPTION_OPEN_INTEREST_LARGE_TRADERS("TaiwanOptionOpenInterestLargeTraders", Tier.BACKER, true, false, true),
    TAIWAN_FUTURES_SPREAD_TRADING("TaiwanFuturesSpreadTrading", Tier.BACKER, true, false, false),
    TAIWAN_FUTURES_FINAL_SETTLEMENT_PRICE("TaiwanFuturesFinalSettlementPrice", Tier.BACKER, true, false, false),
    TAIWAN_OPTION_FINAL_SETTLEMENT_PRICE("TaiwanOptionFinalSettlementPrice", Tier.BACKER, true, false, false),

    TAIWAN_STOCK_TICK_SNAPSHOT("taiwan_stock_tick_snapshot", Tier.SPONSOR, false, false, true),
    TAIWAN_FUT_OPT_TICK_INFO("TaiwanFutOptTickInfo", Tier.FREE, false, false, false),
    TAIWAN_FUTURES_SNAPSHOT("taiwan_futures_snapshot", Tier.SPONSOR, false, false, true),
    TAIWAN_OPTIONS_SNAPSHOT("taiwan_options_snapshot", Tier.SPONSOR, false, false, true),

    TAIWAN_STOCK_CONVERTIBLE_BOND_INFO("TaiwanStockConvertibleBondInfo", Tier.BACKER, false, false, false),
    TAIWAN_STOCK_CONVERTIBLE_BOND_DAILY("TaiwanStockConvertibleBondDaily", Tier.BACKER, true, false, true),
    TAIWAN_STOCK_CONVERTIBLE_BOND_INSTITUTIONAL_INVESTORS("TaiwanStockConvertibleBondInstitutionalInvestors", Tier.BACKER, true, false, true),
    TAIWAN_STOCK_CONVERTIBLE_BOND_DAILY_OVERVIEW("TaiwanStockConvertibleBondDailyOverview", Tier.BACKER, true, false, true),

    TAIWAN_STOCK_NEWS("TaiwanStockNews", Tier.FREE, true, true, false),
    TAIWAN_BUSINESS_INDICATOR("TaiwanBusinessIndicator", Tier.BACKER, false, false, false),
    TAIWAN_STOCK_INDUSTRY_CHAIN("TaiwanStockIndustryChain", Tier.BACKER, false, false, false),

    US_STOCK_INFO("USStockInfo", Tier.FREE, false, false, false),
    US_STOCK_PRICE("USStockPrice", Tier.FREE, true, false, false),
    US_STOCK_PRICE_MINUTE("USStockPriceMinute", Tier.BACKER, true, true, false),
    UK_STOCK_INFO("UKStockInfo", Tier.FREE, false, false, false),
    UK_STOCK_PRICE("UKStockPrice", Tier.FREE, true, false, false),
    EUROPE_STOCK_INFO("EuropeStockInfo", Tier.FREE, false, false, false),
    EUROPE_STOCK_PRICE("EuropeStockPrice", Tier.FREE, true, false, false),
    JAPAN_STOCK_INFO("JapanStockInfo", Tier.FREE, false, false, false),
    JAPAN_STOCK_PRICE("JapanStockPrice", Tier.FREE, true, false, false),

    TAIWAN_EXCHANGE_RATE("TaiwanExchangeRate", Tier.FREE, true, false, false),
    INTEREST_RATE("InterestRate", Tier.FREE, true, false, false),
    GOLD_PRICE("GoldPrice", Tier.FREE, false, false, false),
    CRUDE_OIL_PRICES("CrudeOilPrices", Tier.FREE, true, false, false),
    GOVERNMENT_BONDS_YIELD("GovernmentBondsYield", Tier.FREE, true, false, false),
    CNN_FEAR_GREED_INDEX("CnnFearGreedIndex", Tier.BACKER, false, false, false);

    public enum Tier {
        FREE,
        BACKER,
        SPONSOR
    }

    public enum Category {
        BASIC("基本資料"),
        TECHNICAL("技術面"),
        CHIP("籌碼面"),
        FUNDAMENTAL("基本面"),
        FUTURES_OPTIONS("期貨選擇權"),
        CONVERTIBLE_BOND("可轉債"),
        INTERNATIONAL("國際市場"),
        MACRO_OTHER("總經與其他");

        private final String displayNameZh;

        Category(String displayNameZh) {
            this.displayNameZh = displayNameZh;
        }

        public String displayNameZh() {
            return displayNameZh;
        }

        @Override
        public String toString() {
            return displayNameZh;
        }
    }

    private final String apiName;
    private final Tier tier;
    private final boolean dataIdDataset;
    private final boolean singleDayQuery;
    private final boolean allStockSingleDaySupported;

    FinMindDataset(String apiName, Tier tier, boolean dataIdDataset, boolean singleDayQuery,
                   boolean allStockSingleDaySupported) {
        this.apiName = apiName;
        this.tier = tier;
        this.dataIdDataset = dataIdDataset;
        this.singleDayQuery = singleDayQuery;
        this.allStockSingleDaySupported = allStockSingleDaySupported;
    }

    public String apiName() {
        return apiName;
    }

    public Tier tier() {
        return tier;
    }

    public String displayNameZh() {
        return switch (this) {
            case TAIWAN_STOCK_INFO -> "台股基本資料";
            case TAIWAN_STOCK_INFO_WITH_WARRANT -> "台股含權證基本資料";
            case TAIWAN_STOCK_INFO_WITH_WARRANT_SUMMARY -> "台股含權證彙總";
            case TAIWAN_STOCK_TRADING_DATE -> "台股交易日";
            case TAIWAN_STOCK_PRICE -> "台股日成交資訊";
            case TAIWAN_STOCK_PRICE_ADJ -> "台股還原股價";
            case TAIWAN_STOCK_PRICE_TICK -> "台股逐筆成交";
            case TAIWAN_STOCK_PER -> "台股本益比股價淨值比殖利率";
            case TAIWAN_STOCK_STATISTICS_OF_ORDER_BOOK_AND_TRADE -> "台股委託簿與成交統計";
            case TAIWAN_VARIOUS_INDICATORS_5_SECONDS -> "台股大盤 5 秒指標";
            case TAIWAN_STOCK_DAY_TRADING -> "台股當沖交易統計";
            case TAIWAN_STOCK_TOTAL_RETURN_INDEX -> "台股報酬指數";
            case TAIWAN_STOCK_10_YEAR -> "台股十年線資料";
            case TAIWAN_STOCK_K_BAR -> "台股分 K 資料";
            case TAIWAN_STOCK_WEEK_PRICE -> "台股週 K 資料";
            case TAIWAN_STOCK_MONTH_PRICE -> "台股月 K 資料";
            case TAIWAN_STOCK_EVERY_5_SECONDS_INDEX -> "台股每 5 秒指數";
            case TAIWAN_STOCK_SUSPENDED -> "台股停復牌資料";
            case TAIWAN_STOCK_DAY_TRADING_SUSPENSION -> "台股暫停先賣後買當沖";
            case TAIWAN_STOCK_PRICE_LIMIT -> "台股漲跌停價格";
            case TAIWAN_STOCK_MARGIN_PURCHASE_SHORT_SALE -> "台股融資融券";
            case TAIWAN_STOCK_TOTAL_MARGIN_PURCHASE_SHORT_SALE -> "台股整體融資融券";
            case TAIWAN_STOCK_INSTITUTIONAL_INVESTORS_BUY_SELL -> "三大法人買賣超";
            case TAIWAN_STOCK_TOTAL_INSTITUTIONAL_INVESTORS -> "三大法人總買賣超";
            case TAIWAN_STOCK_SHAREHOLDING -> "台股股權分散表";
            case TAIWAN_STOCK_HOLDING_SHARES_PER -> "台股持股比例";
            case TAIWAN_STOCK_SECURITIES_LENDING -> "台股借券成交";
            case TAIWAN_STOCK_MARGIN_SHORT_SALE_SUSPENSION -> "融券賣出暫停";
            case TAIWAN_DAILY_SHORT_SALE_BALANCES -> "每日賣空餘額";
            case TAIWAN_SECURITIES_TRADER_INFO -> "券商分點基本資料";
            case TAIWAN_STOCK_TRADING_DAILY_REPORT -> "個股分點成交明細";
            case TAIWAN_STOCK_WARRANT_TRADING_DAILY_REPORT -> "權證分點成交明細";
            case TAIWAN_STOCK_GOVERNMENT_BANK_BUY_SELL -> "八大公股銀行買賣超";
            case TAIWAN_TOTAL_EXCHANGE_MARGIN_MAINTENANCE -> "整體融資維持率";
            case TAIWAN_STOCK_TRADING_DAILY_REPORT_SEC_ID_AGG -> "個股分點成交彙總";
            case TAIWAN_STOCK_BLOCK_TRADING_DAILY_REPORT -> "鉅額交易日報";
            case TAIWAN_STOCK_BLOCK_TRADE -> "個股鉅額交易";
            case TAIWAN_STOCK_LOAN_COLLATERAL_BALANCE -> "不限用途款項借貸擔保品餘額";
            case TAIWAN_STOCK_DISPOSITION_SECURITIES_PERIOD -> "處置有價證券期間";
            case TAIWAN_STOCK_FINANCIAL_STATEMENTS -> "綜合損益表";
            case TAIWAN_STOCK_BALANCE_SHEET -> "資產負債表";
            case TAIWAN_STOCK_CASH_FLOWS_STATEMENT -> "現金流量表";
            case TAIWAN_STOCK_DIVIDEND -> "股利政策";
            case TAIWAN_STOCK_DIVIDEND_RESULT -> "除權息結果";
            case TAIWAN_STOCK_MONTH_REVENUE -> "月營收";
            case TAIWAN_STOCK_CAPITAL_REDUCTION_REFERENCE_PRICE -> "減資參考價";
            case TAIWAN_STOCK_MARKET_VALUE -> "市值資料";
            case TAIWAN_STOCK_DELISTING -> "下市櫃資料";
            case TAIWAN_STOCK_MARKET_VALUE_WEIGHT -> "市值比重";
            case TAIWAN_STOCK_SPLIT_PRICE -> "股票分割價格";
            case TAIWAN_STOCK_PAR_VALUE_CHANGE -> "面額變更";
            case TAIWAN_FUT_OPT_DAILY_INFO -> "期貨選擇權每日資訊";
            case TAIWAN_FUTURES_DAILY -> "期貨日成交";
            case TAIWAN_OPTION_DAILY -> "選擇權日成交";
            case TAIWAN_FUTURES_TICK -> "期貨逐筆成交";
            case TAIWAN_OPTION_TICK -> "選擇權逐筆成交";
            case TAIWAN_FUTURES_INSTITUTIONAL_INVESTORS -> "期貨三大法人";
            case TAIWAN_OPTION_INSTITUTIONAL_INVESTORS -> "選擇權三大法人";
            case TAIWAN_FUTURES_INSTITUTIONAL_INVESTORS_AFTER_HOURS -> "盤後期貨三大法人";
            case TAIWAN_OPTION_INSTITUTIONAL_INVESTORS_AFTER_HOURS -> "盤後選擇權三大法人";
            case TAIWAN_FUTURES_DEALER_TRADING_VOLUME_DAILY -> "期貨自營商成交量";
            case TAIWAN_OPTION_DEALER_TRADING_VOLUME_DAILY -> "選擇權自營商成交量";
            case TAIWAN_FUTURES_OPEN_INTEREST_LARGE_TRADERS -> "期貨大額交易人未平倉";
            case TAIWAN_OPTION_OPEN_INTEREST_LARGE_TRADERS -> "選擇權大額交易人未平倉";
            case TAIWAN_FUTURES_SPREAD_TRADING -> "期貨價差交易";
            case TAIWAN_FUTURES_FINAL_SETTLEMENT_PRICE -> "期貨最後結算價";
            case TAIWAN_OPTION_FINAL_SETTLEMENT_PRICE -> "選擇權最後結算價";
            case TAIWAN_STOCK_TICK_SNAPSHOT -> "台股即時快照";
            case TAIWAN_FUT_OPT_TICK_INFO -> "期貨選擇權逐筆商品資訊";
            case TAIWAN_FUTURES_SNAPSHOT -> "期貨即時快照";
            case TAIWAN_OPTIONS_SNAPSHOT -> "選擇權即時快照";
            case TAIWAN_STOCK_CONVERTIBLE_BOND_INFO -> "可轉債基本資料";
            case TAIWAN_STOCK_CONVERTIBLE_BOND_DAILY -> "可轉債日成交";
            case TAIWAN_STOCK_CONVERTIBLE_BOND_INSTITUTIONAL_INVESTORS -> "可轉債法人買賣超";
            case TAIWAN_STOCK_CONVERTIBLE_BOND_DAILY_OVERVIEW -> "可轉債日成交彙總";
            case TAIWAN_STOCK_NEWS -> "台股新聞";
            case TAIWAN_BUSINESS_INDICATOR -> "台灣景氣指標";
            case TAIWAN_STOCK_INDUSTRY_CHAIN -> "台股產業鏈";
            case US_STOCK_INFO -> "美股基本資料";
            case US_STOCK_PRICE -> "美股日成交";
            case US_STOCK_PRICE_MINUTE -> "美股分 K";
            case UK_STOCK_INFO -> "英股基本資料";
            case UK_STOCK_PRICE -> "英股日成交";
            case EUROPE_STOCK_INFO -> "歐股基本資料";
            case EUROPE_STOCK_PRICE -> "歐股日成交";
            case JAPAN_STOCK_INFO -> "日股基本資料";
            case JAPAN_STOCK_PRICE -> "日股日成交";
            case TAIWAN_EXCHANGE_RATE -> "台灣匯率";
            case INTEREST_RATE -> "利率";
            case GOLD_PRICE -> "黃金價格";
            case CRUDE_OIL_PRICES -> "原油價格";
            case GOVERNMENT_BONDS_YIELD -> "政府公債殖利率";
            case CNN_FEAR_GREED_INDEX -> "CNN 恐懼貪婪指數";
        };
    }

    public String tierDisplayNameZh() {
        return switch (tier) {
            case FREE -> "免費";
            case BACKER -> "Backer";
            case SPONSOR -> "Sponsor";
        };
    }

    public Category category() {
        return switch (this) {
            case TAIWAN_STOCK_INFO,
                 TAIWAN_STOCK_INFO_WITH_WARRANT,
                 TAIWAN_STOCK_INFO_WITH_WARRANT_SUMMARY,
                 TAIWAN_STOCK_TRADING_DATE,
                 TAIWAN_STOCK_SUSPENDED,
                 TAIWAN_STOCK_DAY_TRADING_SUSPENSION,
                 TAIWAN_STOCK_PRICE_LIMIT,
                 TAIWAN_SECURITIES_TRADER_INFO,
                 TAIWAN_STOCK_DELISTING,
                 TAIWAN_STOCK_SPLIT_PRICE,
                 TAIWAN_STOCK_PAR_VALUE_CHANGE,
                 TAIWAN_FUT_OPT_TICK_INFO,
                 TAIWAN_STOCK_INDUSTRY_CHAIN -> Category.BASIC;

            case TAIWAN_STOCK_PRICE,
                 TAIWAN_STOCK_PRICE_ADJ,
                 TAIWAN_STOCK_PRICE_TICK,
                 TAIWAN_STOCK_PER,
                 TAIWAN_STOCK_STATISTICS_OF_ORDER_BOOK_AND_TRADE,
                 TAIWAN_VARIOUS_INDICATORS_5_SECONDS,
                 TAIWAN_STOCK_DAY_TRADING,
                 TAIWAN_STOCK_TOTAL_RETURN_INDEX,
                 TAIWAN_STOCK_10_YEAR,
                 TAIWAN_STOCK_K_BAR,
                 TAIWAN_STOCK_WEEK_PRICE,
                 TAIWAN_STOCK_MONTH_PRICE,
                 TAIWAN_STOCK_EVERY_5_SECONDS_INDEX,
                 TAIWAN_STOCK_TICK_SNAPSHOT,
                 TAIWAN_FUTURES_SNAPSHOT,
                 TAIWAN_OPTIONS_SNAPSHOT -> Category.TECHNICAL;

            case TAIWAN_STOCK_MARGIN_PURCHASE_SHORT_SALE,
                 TAIWAN_STOCK_TOTAL_MARGIN_PURCHASE_SHORT_SALE,
                 TAIWAN_STOCK_INSTITUTIONAL_INVESTORS_BUY_SELL,
                 TAIWAN_STOCK_TOTAL_INSTITUTIONAL_INVESTORS,
                 TAIWAN_STOCK_SHAREHOLDING,
                 TAIWAN_STOCK_HOLDING_SHARES_PER,
                 TAIWAN_STOCK_SECURITIES_LENDING,
                 TAIWAN_STOCK_MARGIN_SHORT_SALE_SUSPENSION,
                 TAIWAN_DAILY_SHORT_SALE_BALANCES,
                 TAIWAN_STOCK_TRADING_DAILY_REPORT,
                 TAIWAN_STOCK_WARRANT_TRADING_DAILY_REPORT,
                 TAIWAN_STOCK_GOVERNMENT_BANK_BUY_SELL,
                 TAIWAN_TOTAL_EXCHANGE_MARGIN_MAINTENANCE,
                 TAIWAN_STOCK_TRADING_DAILY_REPORT_SEC_ID_AGG,
                 TAIWAN_STOCK_BLOCK_TRADING_DAILY_REPORT,
                 TAIWAN_STOCK_BLOCK_TRADE,
                 TAIWAN_STOCK_LOAN_COLLATERAL_BALANCE,
                 TAIWAN_STOCK_DISPOSITION_SECURITIES_PERIOD -> Category.CHIP;

            case TAIWAN_STOCK_FINANCIAL_STATEMENTS,
                 TAIWAN_STOCK_BALANCE_SHEET,
                 TAIWAN_STOCK_CASH_FLOWS_STATEMENT,
                 TAIWAN_STOCK_DIVIDEND,
                 TAIWAN_STOCK_DIVIDEND_RESULT,
                 TAIWAN_STOCK_MONTH_REVENUE,
                 TAIWAN_STOCK_CAPITAL_REDUCTION_REFERENCE_PRICE,
                 TAIWAN_STOCK_MARKET_VALUE,
                 TAIWAN_STOCK_MARKET_VALUE_WEIGHT -> Category.FUNDAMENTAL;

            case TAIWAN_FUT_OPT_DAILY_INFO,
                 TAIWAN_FUTURES_DAILY,
                 TAIWAN_OPTION_DAILY,
                 TAIWAN_FUTURES_TICK,
                 TAIWAN_OPTION_TICK,
                 TAIWAN_FUTURES_INSTITUTIONAL_INVESTORS,
                 TAIWAN_OPTION_INSTITUTIONAL_INVESTORS,
                 TAIWAN_FUTURES_INSTITUTIONAL_INVESTORS_AFTER_HOURS,
                 TAIWAN_OPTION_INSTITUTIONAL_INVESTORS_AFTER_HOURS,
                 TAIWAN_FUTURES_DEALER_TRADING_VOLUME_DAILY,
                 TAIWAN_OPTION_DEALER_TRADING_VOLUME_DAILY,
                 TAIWAN_FUTURES_OPEN_INTEREST_LARGE_TRADERS,
                 TAIWAN_OPTION_OPEN_INTEREST_LARGE_TRADERS,
                 TAIWAN_FUTURES_SPREAD_TRADING,
                 TAIWAN_FUTURES_FINAL_SETTLEMENT_PRICE,
                 TAIWAN_OPTION_FINAL_SETTLEMENT_PRICE -> Category.FUTURES_OPTIONS;

            case TAIWAN_STOCK_CONVERTIBLE_BOND_INFO,
                 TAIWAN_STOCK_CONVERTIBLE_BOND_DAILY,
                 TAIWAN_STOCK_CONVERTIBLE_BOND_INSTITUTIONAL_INVESTORS,
                 TAIWAN_STOCK_CONVERTIBLE_BOND_DAILY_OVERVIEW -> Category.CONVERTIBLE_BOND;

            case US_STOCK_INFO,
                 US_STOCK_PRICE,
                 US_STOCK_PRICE_MINUTE,
                 UK_STOCK_INFO,
                 UK_STOCK_PRICE,
                 EUROPE_STOCK_INFO,
                 EUROPE_STOCK_PRICE,
                 JAPAN_STOCK_INFO,
                 JAPAN_STOCK_PRICE -> Category.INTERNATIONAL;

            case TAIWAN_STOCK_NEWS,
                 TAIWAN_BUSINESS_INDICATOR,
                 TAIWAN_EXCHANGE_RATE,
                 INTEREST_RATE,
                 GOLD_PRICE,
                 CRUDE_OIL_PRICES,
                 GOVERNMENT_BONDS_YIELD,
                 CNN_FEAR_GREED_INDEX -> Category.MACRO_OTHER;
        };
    }

    public boolean isDataIdDataset() {
        return dataIdDataset;
    }

    public boolean isSingleDayQuery() {
        return singleDayQuery;
    }

    public boolean isAllStockSingleDaySupported() {
        return allStockSingleDaySupported;
    }

    public boolean requiresSponsor() {
        return tier == Tier.SPONSOR;
    }

    public boolean requiresBacker() {
        return tier == Tier.BACKER;
    }

    public boolean requiresPaidTier() {
        return tier != Tier.FREE;
    }
}
