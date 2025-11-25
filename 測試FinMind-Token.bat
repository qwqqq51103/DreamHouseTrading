@echo off
chcp 65001 > nul
cd /d "%~dp0"

echo ========================================
echo 測試 FinMind API Token
echo ========================================
echo.

REM 讀取 Token
for /f "tokens=2 delims==" %%a in ('type datasource.properties ^| findstr /i "finmind.apitoken"') do set TOKEN=%%a

if "%TOKEN%"=="" (
    echo [錯誤] 未找到 Token！
    echo 請檢查 datasource.properties 檔案
    pause
    exit /b 1
)

echo [資訊] Token（前20字元）: %TOKEN:~0,20%...
echo.

echo ----------------------------------------
echo 測試 1: TaiwanStockPrice API（免費）
echo ----------------------------------------
echo.

curl -s "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockPrice&data_id=2330&start_date=2025-11-01&end_date=2025-11-01&token=%TOKEN%" > temp_response.json

type temp_response.json | find "status" > nul
if %ERRORLEVEL% equ 0 (
    echo [✓] API 響應成功
    type temp_response.json | find "msg"
    type temp_response.json | find "status"
) else (
    echo [✗] API 無響應
)

echo.
echo ----------------------------------------
echo 測試 2: TaiwanStockKBar API（Sponsor）
echo ----------------------------------------
echo.

curl -s "https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockKBar&data_id=2330&start_date=2025-11-21&token=%TOKEN%" > temp_response2.json

type temp_response2.json | find "status" > nul
if %ERRORLEVEL% equ 0 (
    echo [✓] API 響應成功
    type temp_response2.json | find "msg"
    type temp_response2.json | find "status"
) else (
    echo [✗] API 無響應
)

del temp_response.json temp_response2.json 2>nul

echo.
echo ========================================
echo 測試完成
echo ========================================
echo.
echo 請根據上述結果：
echo 1. 如果 status=200，Token 有效
echo 2. 如果 msg="Token is illegal"，請重新取得 Token
echo 3. 如果 TaiwanStockKBar 失敗，可能需要 Sponsor 會員
echo.
pause
