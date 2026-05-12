package com.dreamhouse.trading.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StockNameResolver {

    private static final Map<String, String> NAMES = new ConcurrentHashMap<>();

    static {
        register("1101", "台泥");
        register("1102", "亞泥");
        register("1216", "統一");
        register("1301", "台塑");
        register("1303", "南亞");
        register("1326", "台化");
        register("2002", "中鋼");
        register("2207", "和泰車");
        register("2303", "聯電");
        register("2308", "台達電");
        register("2317", "鴻海");
        register("2327", "國巨");
        register("2330", "台積電");
        register("2345", "智邦");
        register("2357", "華碩");
        register("2379", "瑞昱");
        register("2382", "廣達");
        register("2395", "研華");
        register("2408", "南亞科");
        register("2412", "中華電");
        register("2454", "聯發科");
        register("2603", "長榮");
        register("2609", "陽明");
        register("2615", "萬海");
        register("2880", "華南金");
        register("2881", "富邦金");
        register("2882", "國泰金");
        register("2883", "開發金");
        register("2884", "玉山金");
        register("2885", "元大金");
        register("2886", "兆豐金");
        register("2887", "台新金");
        register("2890", "永豐金");
        register("2891", "中信金");
        register("2892", "第一金");
        register("3008", "大立光");
        register("3034", "聯詠");
        register("3045", "台灣大");
        register("3231", "緯創");
        register("3711", "日月光投控");
        register("4904", "遠傳");
        register("5871", "中租-KY");
        register("5880", "合庫金");
        register("6505", "台塑化");
        register("6669", "緯穎");
        register("6770", "力積電");
    }

    private StockNameResolver() {
    }

    public static String resolveChineseName(String symbol) {
        return NAMES.getOrDefault(normalize(symbol), "");
    }

    public static void register(String symbol, String chineseName) {
        String normalized = normalize(symbol);
        if (!normalized.isBlank() && chineseName != null && !chineseName.isBlank()) {
            NAMES.put(normalized, chineseName.trim());
        }
    }

    public static String normalize(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim()
                .toUpperCase()
                .replace(".TW", "")
                .replace(".TWO", "");
    }
}
