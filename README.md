# 今日財經直播自動摘要

每天 09:30 自動：找到頻道當天已結束的直播 → 下載音訊 → Gemini 摘要 → Telegram / Email 通知。

## 設定
1. `copy .env.example .env`，填入 `CHANNEL_URL`、`GEMINI_API_KEY`，以及 Telegram / Email 欄位（不用的留空）。
2. 需要 Python 3.10+ 與 ffmpeg（已在 PATH）。
3. 手動測試：`run.bat`（首次會建立 .venv），輸出看 `digest.log`。
4. 排程：執行 `install_schedule.bat`。

## 備註
- 已處理的影片記錄在 `state.json`，重跑不會重複發送。
- 回傳碼 2 = 今天還沒有已結束的直播。
- Gmail 需開兩步驟驗證後建立「應用程式密碼」；Telegram 用 @BotFather 建 bot，並用 `getUpdates` 取得 chat id。
