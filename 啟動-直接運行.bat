@echo off
chcp 65001 > nul
cd /d "%~dp0"

echo ========================================
echo 啟動 DreamHouseTrading（直接運行）
echo ========================================
echo.

REM 設定 Java 路徑
set JAVA_HOME=C:\Program Files\Java\jdk-17
set JAVA="%JAVA_HOME%\bin\java.exe"

REM 先編譯（如果需要）
echo [1/2] 編譯專案...
call "C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd" compile -q
if %ERRORLEVEL% neq 0 (
    echo 編譯失敗！
    pause
    exit /b 1
)

echo [2/2] 啟動應用程式...
echo.

REM 構建 classpath
set CP=target\classes
set CP=%CP%;lib\market-data-collector-1.0.0.jar

REM 添加所有 Maven 依賴
for /r target\classes %%i in (*.jar) do set CP=%CP%;%%i
for /r "%USERPROFILE%\.m2\repository" %%i in (*.jar) do set CP=%CP%;%%i

REM 運行程式
%JAVA% -cp "%CP%" -Dfile.encoding=UTF-8 com.dreamhouse.trading.Main

echo.
echo 程式已關閉
pause
