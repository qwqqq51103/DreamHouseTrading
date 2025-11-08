@echo off
echo ============================================
echo DreamHouse Trading - 重新構建項目
echo ============================================
echo.

echo [1/3] 清理舊的編譯文件...
call mvn clean

echo.
echo [2/3] 編譯項目...
call mvn compile

echo.
echo [3/3] 打包項目...
call mvn package -DskipTests

echo.
echo ============================================
echo 構建完成！
echo ============================================
echo.
echo 現在可以在 NetBeans 中運行項目了
echo 或者執行: mvn exec:java
echo.
pause



