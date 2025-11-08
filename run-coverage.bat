@echo off
REM DreamHouse Trading - 代碼覆蓋率報告生成腳本

echo ========================================
echo DreamHouse Trading 代碼覆蓋率報告
echo ========================================
echo.

set JAVA_HOME=C:\Program Files\Java\jdk-17
set MAVEN_CMD=C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd

echo 執行測試並生成覆蓋率報告...
echo.

"%MAVEN_CMD%" clean test

echo.
echo ========================================
echo 報告生成完成
echo ========================================
echo.
echo 覆蓋率報告位置: target\site\jacoco\index.html
echo.
echo 正在打開報告...
start target\site\jacoco\index.html

pause
