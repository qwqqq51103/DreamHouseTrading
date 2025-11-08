@echo off
REM 安裝 Git Hooks 腳本

echo ========================================
echo 安裝 Git Hooks
echo ========================================
echo.

REM 檢查 .git 目錄是否存在
if not exist ".git" (
    echo ❌ 錯誤：當前目錄不是 Git 倉庫
    pause
    exit /b 1
)

REM 創建 hooks 目錄（如果不存在）
if not exist ".git\hooks" (
    mkdir ".git\hooks"
)

REM 複製 pre-commit hook
echo 安裝 pre-commit hook...

(
echo #!/bin/sh
echo # Git Pre-Commit Hook
echo # 在提交前自動執行測試
echo.
echo echo "======================================"
echo echo "Git Pre-Commit Hook: 執行測試"
echo echo "======================================"
echo echo ""
echo.
echo # 設置環境變數
echo export JAVA_HOME="C:/Program Files/Java/jdk-17"
echo export PATH="$JAVA_HOME/bin:$PATH"
echo.
echo # 設置 Maven
echo MAVEN_CMD="C:/Program Files/NetBeans-17/netbeans/java/maven/bin/mvn.cmd"
echo.
echo # 執行測試
echo echo "執行單元測試..."
echo "$MAVEN_CMD" test -q
echo.
echo # 檢查測試結果
echo if [ $? -ne 0 ]; then
echo     echo ""
echo     echo "❌ 測試失敗！請修復測試後再提交。"
echo     echo "   提示：執行 'run-tests.bat' 查看詳細錯誤"
echo     echo ""
echo     exit 1
echo fi
echo.
echo echo ""
echo echo "✅ 所有測試通過！繼續提交..."
echo echo ""
echo exit 0
) > ".git\hooks\pre-commit"

echo.
echo ✅ Git Hooks 安裝成功！
echo.
echo 現在每次提交前都會自動執行測試。
echo.
echo 如果需要跳過測試直接提交，使用：
echo   git commit --no-verify -m "your message"
echo.
pause
