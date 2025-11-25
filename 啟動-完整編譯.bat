@echo off
chcp 65001 > nul
cd /d "%~dp0"

echo ========================================
echo 啟動 DreamHouseTrading（完整編譯）
echo ========================================
echo.
echo [1/2] 正在編譯專案...
echo.

call "C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd" clean compile

if %ERRORLEVEL% neq 0 (
    echo.
    echo 編譯失敗！請檢查錯誤訊息。
    pause
    exit /b 1
)

echo.
echo [2/2] 啟動應用程式...
echo.

call "C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd" exec:java -Dexec.mainClass=com.dreamhouse.trading.Main

echo.
echo 程式已關閉
pause
