@echo off
REM DreamHouse Trading - 單元測試執行腳本
REM 使用 NetBeans 內建的 Maven 執行測試

echo ========================================
echo DreamHouse Trading 單元測試
echo ========================================
echo.

set JAVA_HOME=C:\Program Files\Java\jdk-17
set MAVEN_CMD=C:\Program Files\NetBeans-17\netbeans\java\maven\bin\mvn.cmd

echo Java 版本:
"%JAVA_HOME%\bin\java.exe" -version
echo.

echo 開始執行測試...
echo.

"%MAVEN_CMD%" clean test

echo.
echo ========================================
echo 測試完成
echo ========================================
echo 測試報告位置: target\surefire-reports
echo.

pause
