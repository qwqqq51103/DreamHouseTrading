# 🤖 AI 團隊使用指南

本專案配置了 5 個 AI 團隊角色，透過 Claude Code 的 slash commands 實現。

## 👥 團隊成員

| 角色 | 命令 | 職責 | 使用時機 |
|------|------|------|----------|
| 🎯 **PM** | `/pm-review` | 專案管理、文檔審查、進度追蹤 | 每週檢查、發布前審查 |
| 🧪 **QA** | `/qa-test` | 代碼品質、測試覆蓋、bug 檢測 | 提交前、發現問題後 |
| 🏗️ **架構師** | `/architect-review` | 系統架構、設計模式、技術選型 | 重大重構前、技術決策時 |
| 🚀 **DevOps** | `/devops-check` | 建置流程、部署配置、監控 | 發布前、環境問題時 |
| 🤝 **團隊** | `/team-standup` | 整體狀況、跨角色協作 | 每日開始、專案檢查 |

## 🎯 快速開始

### 日常使用流程

1. **每天開始** - 執行團隊 Standup
   ```
   /team-standup
   ```

2. **提交代碼前** - QA 檢查
   ```
   /qa-test
   ```

3. **每週檢查** - PM 審查
   ```
   /pm-review
   ```

4. **重大變更前** - 架構師審查
   ```
   /architect-review
   ```

5. **發布前** - DevOps 檢查
   ```
   /devops-check
   ```

## 📋 使用場景

### 場景 1: 新功能開發完成
```bash
# 1. QA 檢查代碼品質
/qa-test

# 2. 如有架構變更，執行架構審查
/architect-review

# 3. PM 確認文檔更新
/pm-review
```

### 場景 2: 準備發布新版本
```bash
# 1. 團隊整體檢查
/team-standup

# 2. DevOps 確認建置流程
/devops-check

# 3. QA 最終測試
/qa-test

# 4. PM 確認交付品
/pm-review
```

### 場景 3: 發現嚴重 Bug
```bash
# 1. QA 深入分析
/qa-test

# 2. 架構師評估影響範圍
/architect-review

# 3. 團隊討論解決方案
/team-standup
```

### 場景 4: 技術債務評估
```bash
# 1. 架構師識別問題
/architect-review

# 2. PM 評估優先級
/pm-review

# 3. DevOps 評估改善影響
/devops-check
```

## 🔧 自定義團隊

您可以在 `.claude/commands/` 目錄新增更多角色：

### 創建新角色範例：Security Engineer

創建 `.claude/commands/security-audit.md`：

```markdown
# Security 安全審查

你現在是安全工程師，負責審查安全漏洞和風險。

## 你的職責
1. 檢查常見安全漏洞（OWASP Top 10）
2. 審查密鑰和敏感資訊管理
3. 評估輸入驗證和輸出編碼
4. 檢查認證和授權機制

## 執行安全審查
[詳細指令...]

開始安全審查！
```

使用：`/security-audit`

## 💡 最佳實踐

### 1. 定期檢查節奏
- **每日**: `/team-standup` (早上開始)
- **每週**: `/pm-review` (週五下午)
- **每次提交前**: `/qa-test`
- **重大變更**: `/architect-review`
- **發布前**: 所有角色全部檢查

### 2. 報告處理
每次審查後：
1. 記錄發現的問題
2. 評估優先級（高/中/低）
3. 建立 GitHub Issues 追蹤
4. 更新文檔

### 3. 團隊協作
- 將審查報告分享給團隊
- 定期討論改善方案
- 追蹤問題解決進度

## 📊 指標追蹤

建議追蹤以下指標：

| 指標 | 來源 | 目標 |
|------|------|------|
| 測試覆蓋率 | QA 報告 | > 80% |
| 測試通過率 | QA 報告 | 100% |
| 建置時間 | DevOps 報告 | < 2 分鐘 |
| 文檔完整度 | PM 報告 | 100% |
| 技術債務 | 架構師報告 | 持續減少 |
| 安全風險 | QA 報告 | 0 嚴重 |

## 🎓 進階使用

### 整合到 Git Workflow

在 `.git/hooks/pre-commit` 中自動執行 QA 檢查：

```bash
#!/bin/bash
echo "執行 QA 自動檢查..."
# 可以整合 mvn test 等命令
```

### 結合 GitHub Actions

創建 `.github/workflows/ai-team-review.yml`：

```yaml
name: AI Team Review
on: [pull_request]
jobs:
  qa-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v2
      - name: Run Tests
        run: mvn test
      # 可以添加更多檢查
```

## 🆘 常見問題

**Q: 報告太長怎麼辦？**
A: 每個 command 都可以加上具體範圍限制，如 "僅審查最近修改的檔案"

**Q: 如何自動化執行？**
A: 可以整合到 Git hooks 或 CI/CD pipeline

**Q: 可以同時執行多個角色嗎？**
A: `/team-standup` 就是同時執行所有角色的快捷方式

**Q: 報告結果如何保存？**
A: 建議將報告結果記錄到 GitHub Issues 或專案文檔中

## 📚 延伸閱讀

- [Claude Code 官方文檔](https://claude.ai/claude-code)
- [Slash Commands 指南](https://docs.anthropic.com/claude-code/commands)
- [專案文檔](PROJECT_DOCUMENTATION.md)

---

**提示**: 這些 AI 團隊成員是您的得力助手，但最終決策權仍在您手中。請批判性地評估每個建議，並根據專案實際情況調整。
