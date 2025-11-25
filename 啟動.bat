@echo off
chcp 65001 > nul
cd /d "%~dp0"

echo ========================================
echo 啟動 DreamHouseTrading
echo ========================================
echo.
echo 提示：首次啟動或修改代碼後，請使用「啟動-完整編譯.bat」
echo.
echo 正在啟動應用程式...
echo.

REM 使用 Maven exec:java，會自動載入所有依賴（包括 lib 中的 JAR）
call "C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd" exec:java -Dexec.mainClass=com.dreamhouse.trading.Main

echo.
echo 程式已關閉
pause
