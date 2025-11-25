@echo off
chcp 65001 > nul
cd /d "%~dp0"

echo ========================================
echo 啟動 DreamHouseTrading（完整版 - 含資料庫整合）
echo ========================================
echo.
echo 正在啟動應用程式...
echo.

REM 使用 Maven 運行，確保所有依賴都正確載入
"C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd" clean compile exec:java -Dexec.mainClass=com.dreamhouse.trading.Main -q

echo.
echo 程式已關閉
pause
