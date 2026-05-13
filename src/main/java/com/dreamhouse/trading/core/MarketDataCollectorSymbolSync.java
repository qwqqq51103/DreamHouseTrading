package com.dreamhouse.trading.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Keeps DreamHouseTrading's watchlist aligned with MarketDataCollector's symbol file.
 */
public final class MarketDataCollectorSymbolSync {
    private static final String SYMBOLS_PATH_PROPERTY = "marketcollector.symbols.path";
    private static final String SYMBOLS_PATH_ENV = "MARKET_COLLECTOR_SYMBOLS_PATH";

    private MarketDataCollectorSymbolSync() {
    }

    public static Path resolveSymbolsPath() {
        String configured = System.getProperty(SYMBOLS_PATH_PROPERTY);
        if (configured == null || configured.isBlank()) {
            configured = System.getenv(SYMBOLS_PATH_ENV);
        }
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured.trim());
        }

        Path cwd = Path.of("").toAbsolutePath().normalize();
        Path parent = cwd.getParent();
        if (parent == null) {
            return null;
        }
        return parent.resolve("MarketDataCollector").resolve("config").resolve("symbols.properties").normalize();
    }

    public static boolean addSymbol(String symbol) {
        Path path = resolveSymbolsPath();
        if (path == null) {
            return false;
        }
        String collectorSymbol = toCollectorSymbol(symbol);
        if (collectorSymbol.isBlank()) {
            return false;
        }
        try {
            Files.createDirectories(path.getParent());
            List<String> lines = Files.exists(path)
                    ? Files.readAllLines(path, StandardCharsets.UTF_8)
                    : new ArrayList<>();
            String normalized = StockNameResolver.normalize(collectorSymbol);
            for (int i = 0; i < lines.size(); i++) {
                ParsedSymbol parsed = parseSymbolLine(lines.get(i));
                if (parsed != null && StockNameResolver.normalize(parsed.symbol()).equals(normalized)) {
                    String detectedName = resolveName(collectorSymbol, parsed.name());
                    if (parsed.active()) {
                        if (!isSameName(parsed.name(), detectedName)) {
                            lines.set(i, collectorSymbol + "=" + detectedName);
                            writeLines(path, lines);
                            StockNameResolver.loadMarketDataCollectorNames();
                        }
                        return true;
                    }
                    lines.set(i, collectorSymbol + "=" + detectedName);
                    writeLines(path, lines);
                    StockNameResolver.loadMarketDataCollectorNames();
                    return true;
                }
            }
            if (!lines.isEmpty() && !lines.get(lines.size() - 1).isBlank()) {
                lines.add("");
            }
            lines.add(collectorSymbol + "=" + resolveName(collectorSymbol, ""));
            writeLines(path, lines);
            StockNameResolver.loadMarketDataCollectorNames();
            return true;
        } catch (IOException e) {
            System.err.println("Failed to sync MarketDataCollector symbol add: " + e.getMessage());
            return false;
        }
    }

    public static boolean removeSymbol(String symbol) {
        Path path = resolveSymbolsPath();
        if (path == null || !Files.isRegularFile(path)) {
            return false;
        }
        String normalized = StockNameResolver.normalize(symbol);
        if (normalized.isBlank()) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            boolean changed = false;
            for (int i = 0; i < lines.size(); i++) {
                ParsedSymbol parsed = parseSymbolLine(lines.get(i));
                if (parsed != null && parsed.active()
                        && StockNameResolver.normalize(parsed.symbol()).equals(normalized)) {
                    lines.set(i, "#" + lines.get(i));
                    changed = true;
                }
            }
            if (changed) {
                writeLines(path, lines);
                StockNameResolver.loadMarketDataCollectorNames();
            }
            return changed;
        } catch (IOException e) {
            System.err.println("Failed to sync MarketDataCollector symbol remove: " + e.getMessage());
            return false;
        }
    }

    private static String resolveName(String symbol, String existingName) {
        if (isUsableName(existingName, symbol)) {
            StockNameResolver.register(symbol, existingName);
            return existingName.trim();
        }

        String detectedName = StockNameResolver.detectChineseName(symbol);
        if (isUsableName(detectedName, symbol)) {
            return detectedName.trim();
        }

        String normalized = StockNameResolver.normalize(symbol);
        return normalized.isBlank() ? symbol : normalized;
    }

    private static boolean isSameName(String currentName, String detectedName) {
        String current = currentName != null ? currentName.trim() : "";
        String detected = detectedName != null ? detectedName.trim() : "";
        return current.equals(detected);
    }

    private static boolean isUsableName(String name, String symbol) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String trimmed = name.trim();
        String normalizedSymbol = StockNameResolver.normalize(symbol);
        return !trimmed.equals("--")
                && !trimmed.equalsIgnoreCase(symbol)
                && !trimmed.equalsIgnoreCase(normalizedSymbol);
    }

    private static String toCollectorSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        String value = symbol.trim().toUpperCase(Locale.ROOT);
        if (value.matches("\\d{4}")) {
            return value + ".TW";
        }
        return value;
    }

    private static ParsedSymbol parseSymbolLine(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.trim();
        boolean active = true;
        if (trimmed.startsWith("#")) {
            active = false;
            trimmed = trimmed.substring(1).trim();
        }
        int separator = trimmed.indexOf('=');
        if (separator <= 0) {
            return null;
        }
        String symbol = trimmed.substring(0, separator).trim();
        String name = trimmed.substring(separator + 1).trim();
        return symbol.isBlank() ? null : new ParsedSymbol(symbol, name, active);
    }

    private static void writeLines(Path path, List<String> lines) throws IOException {
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private record ParsedSymbol(String symbol, String name, boolean active) {
    }
}
