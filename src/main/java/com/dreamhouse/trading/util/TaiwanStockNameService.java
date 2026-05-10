package com.dreamhouse.trading.util;

import java.util.HashMap;
import java.util.Map;

/**
 * 台股代號到中文名稱的映射服務
 *
 * 提供台灣股票市場的股票代號與中文名稱對應關係
 */
public class TaiwanStockNameService {

    private static final Map<String, String> STOCK_NAMES = new HashMap<>();

    static {
        // 台灣50成分股
        STOCK_NAMES.put("2330", "台積電");
        STOCK_NAMES.put("2317", "鴻海");
        STOCK_NAMES.put("2454", "聯發科");
        STOCK_NAMES.put("2891", "中信金");
        STOCK_NAMES.put("2882", "國泰金");
        STOCK_NAMES.put("2412", "中華電");
        STOCK_NAMES.put("2308", "台達電");
        STOCK_NAMES.put("2881", "富邦金");
        STOCK_NAMES.put("1301", "台塑");
        STOCK_NAMES.put("1303", "南亞");
        STOCK_NAMES.put("2886", "兆豐金");
        STOCK_NAMES.put("2892", "第一金");
        STOCK_NAMES.put("2002", "中鋼");
        STOCK_NAMES.put("2912", "統一超");
        STOCK_NAMES.put("1326", "台化");
        STOCK_NAMES.put("2382", "廣達");
        STOCK_NAMES.put("2303", "聯電");
        STOCK_NAMES.put("3008", "大立光");
        STOCK_NAMES.put("2884", "玉山金");
        STOCK_NAMES.put("3711", "日月光投控");

        // 熱門個股
        STOCK_NAMES.put("3706", "神達");
        STOCK_NAMES.put("2603", "長榮");
        STOCK_NAMES.put("2609", "陽明");
        STOCK_NAMES.put("2615", "萬海");
        STOCK_NAMES.put("2357", "華碩");
        STOCK_NAMES.put("2395", "研華");
        STOCK_NAMES.put("2301", "光寶科");
        STOCK_NAMES.put("2327", "國巨");
        STOCK_NAMES.put("2409", "友達");
        STOCK_NAMES.put("2474", "可成");
        STOCK_NAMES.put("3231", "緯創");
        STOCK_NAMES.put("3034", "聯詠");
        STOCK_NAMES.put("2408", "南亞科");
        STOCK_NAMES.put("3443", "創意");
        STOCK_NAMES.put("2379", "瑞昱");
        STOCK_NAMES.put("2344", "華邦電");
        STOCK_NAMES.put("2377", "微星");
        STOCK_NAMES.put("2354", "鴻準");
        STOCK_NAMES.put("2356", "英業達");
        STOCK_NAMES.put("2360", "致茂");

        // 半導體相關
        STOCK_NAMES.put("6488", "環球晶");
        STOCK_NAMES.put("3037", "欣興");
        STOCK_NAMES.put("6770", "力積電");
        STOCK_NAMES.put("5274", "信驊");
        STOCK_NAMES.put("5347", "世界");
        STOCK_NAMES.put("3105", "穩懋");
        STOCK_NAMES.put("3661", "世芯-KY");
        STOCK_NAMES.put("6669", "緯穎");

        // 金融股
        STOCK_NAMES.put("2880", "華南金");
        STOCK_NAMES.put("2887", "台新金");
        STOCK_NAMES.put("2883", "開發金");
        STOCK_NAMES.put("2885", "元大金");
        STOCK_NAMES.put("5880", "合庫金");

        // 電子零組件
        STOCK_NAMES.put("2449", "京元電子");
        STOCK_NAMES.put("2353", "宏碁");
        STOCK_NAMES.put("2324", "仁寶");
        STOCK_NAMES.put("2371", "大同");
        STOCK_NAMES.put("6176", "瑞儀");

        // 傳產股
        STOCK_NAMES.put("1216", "統一");
        STOCK_NAMES.put("1101", "台泥");
        STOCK_NAMES.put("2890", "永豐金");
        STOCK_NAMES.put("9910", "豐泰");
        STOCK_NAMES.put("2207", "和泰車");

        // 添加 .TW 後綴版本（支持不同格式）
        Map<String, String> twSuffixMap = new HashMap<>();
        for (Map.Entry<String, String> entry : STOCK_NAMES.entrySet()) {
            twSuffixMap.put(entry.getKey() + ".TW", entry.getValue());
        }
        STOCK_NAMES.putAll(twSuffixMap);
    }

    /**
     * 根據股票代號獲取中文名稱
     *
     * @param symbol 股票代號（可以是 "3706" 或 "3706.TW" 格式）
     * @return 中文名稱，如果找不到則返回 null
     */
    public static String getChineseName(String symbol) {
        if (symbol == null || symbol.trim().isEmpty()) {
            return null;
        }

        // 嘗試直接查詢
        String name = STOCK_NAMES.get(symbol);
        if (name != null) {
            return name;
        }

        // 移除 .TW 後綴再查詢
        if (symbol.endsWith(".TW")) {
            String codeOnly = symbol.substring(0, symbol.length() - 3);
            return STOCK_NAMES.get(codeOnly);
        }

        // 添加 .TW 後綴查詢
        return STOCK_NAMES.get(symbol + ".TW");
    }

    /**
     * 檢查是否有該股票的中文名稱
     */
    public static boolean hasChineseName(String symbol) {
        return getChineseName(symbol) != null;
    }

    /**
     * 格式化顯示：代號 + 中文名稱
     * 例如：3706 神達
     */
    public static String formatWithName(String symbol) {
        String name = getChineseName(symbol);
        if (name != null) {
            // 移除 .TW 後綴（如果有）
            String displaySymbol = symbol.endsWith(".TW") ?
                symbol.substring(0, symbol.length() - 3) : symbol;
            return displaySymbol + " " + name;
        }
        return symbol;
    }

    /**
     * 獲取所有已知的股票代號
     */
    public static Map<String, String> getAllStocks() {
        return new HashMap<>(STOCK_NAMES);
    }

    /**
     * 添加自定義股票名稱（用於擴展）
     */
    public static void addCustomStock(String symbol, String name) {
        STOCK_NAMES.put(symbol, name);
        if (!symbol.endsWith(".TW")) {
            STOCK_NAMES.put(symbol + ".TW", name);
        }
    }
}
