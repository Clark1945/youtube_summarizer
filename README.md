# 今日財經直播自動摘要

每天 09:10 自動：找到頻道當天已結束的直播 → 下載音訊 → Gemini 摘要 → Telegram / Email 通知。

## 設定
1. `copy .env.example .env`，填入 `CHANNEL_URL`、`GEMINI_API_KEY`，以及 Telegram / Email 欄位（不用的留空）。
2. 需要 Python 3.10+ 與 ffmpeg（已在 PATH）。
3. 手動測試：`run.bat`（首次會建立 .venv），輸出看 `digest.log`。
4. 排程：執行 `install_schedule.bat`。

## 備註
- 已處理的影片記錄在 `state.json`，重跑不會重複發送。
- Gmail 需開兩步驟驗證後建立「應用程式密碼」；Telegram 用 @BotFather 建 bot，並用 `getUpdates` 取得 chat id。
- 09:10 時直播可能還沒結束：程式會每分鐘查一次，最多等 `WAIT_PENDING_MINUTES`（預設 60）分鐘。
- 完全找不到今天的直播時，再等 `WAIT_NONE_MINUTES`（預設 10）分鐘，之後會通知「今天沒有直播」（`NOTIFY_NO_STREAM=false` 可關閉）。

## 部署到 Linux server（Docker + cron）
```bash
git clone https://github.com/Clark1945/youtube_summarizer.git && cd youtube_summarizer
cp .env.example .env && nano .env        # 填入金鑰
docker compose build
docker compose run --rm digest           # 手動試跑一次
```
排程：`crontab -e` 加入（主機時區請設成 Asia/Taipei，或把 09:10 換算成主機時區）：
```
10 9 * * * cd /path/to/youtube_summarizer && docker compose run --rm digest >> digest.log 2>&1
```
- 已處理紀錄存在 `./data/state.json`（volume），重建映像不會遺失。
- 若 server 是雲端主機（資料中心 IP），YouTube 可能擋下載（出現 Sign in to confirm you're not a bot）。可改用家用 IP 的機器，或在 yt-dlp 設定 cookies／代理。
