# YouTube Summarizer

輸入 YouTube 網址，自動取得字幕並用 Gemini AI 生成重點摘要，支援多語言輸出。

## 架構

```
Browser
  │  POST /api/summarize { url }
  ▼
Spring Boot :8080  (BFF + 快取層)
  │  GET  /transcript?videoId=   ←── 取字幕
  │  POST /summarize { transcript, language }  ←── 生成摘要
  ▼
media-service :5001  (Flask + Gemini)
  └── YouTube Transcript API → Google Gemini API
```

Spring Boot 僅作 BFF（Backend for Frontend）與快取，**不含任何 AI 邏輯**。
media-service 是無狀態的 Flask 服務，負責字幕抓取與 Gemini 呼叫，可部署至 AWS Lambda（透過 Mangum）。

---

## 技術棧

| 層級 | 技術 |
|------|------|
| 前端 | Thymeleaf（SSR） |
| BFF | Java 17、Spring Boot 3.2.3、Caffeine Cache |
| media-service | Python 3、Flask、youtube-transcript-api、google-genai |
| AI | Google Gemini 2.5 Flash |
| 部署選項 | 本機執行 / AWS Lambda（Mangum） |

---

## 快速開始

### 前置需求

- Java 17+、Maven
- Python 3.9+
- Google Gemini API 金鑰（[取得](https://aistudio.google.com/)）

### 1. 啟動 media-service（Python）

```bash
cd media-service
pip install -r requirements.txt

export GEMINI_API_KEY=your_api_key_here   # Windows: set GEMINI_API_KEY=...
python app.py
# 服務跑在 http://localhost:5001
```

### 2. 啟動 Spring Boot

```bash
# 回到專案根目錄
mvn spring-boot:run
# 服務跑在 http://localhost:8080
```

開啟瀏覽器前往 `http://localhost:8080`，貼上 YouTube 網址即可使用。

---

## 環境變數

### media-service（Python）

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `GEMINI_API_KEY` | **必填** | Google Gemini API 金鑰 |
| `GEMINI_MODEL` | `gemini-2.5-flash` | 使用的 Gemini 模型 |

### Spring Boot

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `MEDIA_SERVICE_URL` | `http://localhost:5001` | media-service 位址 |

---

## API 文件

### Spring Boot（對外）

#### `POST /api/summarize`

接收 YouTube 網址，回傳摘要。語言依瀏覽器的 `Accept-Language` 標頭自動判斷。

**Request**
```json
{ "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ" }
```

**Response 200**
```json
{ "success": true, "summary": "• 重點一\n• 重點二\n..." }
```

**Error Response**
```json
{ "success": false, "error": "錯誤訊息" }
```

| HTTP 狀態碼 | 情境 |
|-------------|------|
| `200` | 成功 |
| `400` | 未提供 URL |
| `404` | 影片不存在或無法存取 |
| `422` | 影片沒有可用字幕 |
| `503` | media-service 連線失敗 |

---

### media-service（內部）

#### `POST /summarize`

接收字幕文字，呼叫 Gemini 生成摘要。

**Request**
```json
{ "transcript": "字幕文字...", "language": "Traditional Chinese (繁體中文)" }
```

**Response 200**
```json
{ "summary": "• 重點一\n- 子項目\n• 重點二\n..." }
```

#### `GET /transcript?videoId={videoId}`

（Debug 用）直接取回原始字幕文字。

**Response 200**
```json
{ "transcript": "字幕文字...", "language": "zh-TW" }
```

| HTTP 狀態碼 | 情境 |
|-------------|------|
| `200` | 成功 |
| `404` | 影片不存在 |
| `422` | 無可用字幕（TranscriptsDisabled / NoTranscriptFound） |
| `503` | 服務內部錯誤 |

---

## 快取機制

Spring Boot 使用 Caffeine Cache，分兩層獨立快取：

| 快取名稱 | Cache Key | TTL | 最大筆數 |
|----------|-----------|-----|---------|
| `transcripts` | `videoId` | 1 小時 | 100 |
| `summaries` | `videoId:language` | 1 小時 | 100 |

相同影片在快取有效期內不會重複呼叫 YouTube 或 Gemini API。

---

## 支援語言

摘要語言依 `Accept-Language` 標頭自動選擇，目前對應：

| 語系代碼 | 輸出語言 |
|----------|---------|
| `zh-TW`、`zh-HK` | 繁體中文 |
| `zh-CN`、`zh` | 簡體中文 / 中文 |
| `en`、`en-US`、`en-GB` | English |
| `ja` | 日本語 |
| `ko` | 한국어 |
| `es` | Español |
| `fr` | Français |
| `de` | Deutsch |
| `pt` | Português |
| `it` | Italiano |

未匹配的語系預設使用英文。

---

## 專案結構

```
youtube-summarizer/
├── media-service/
│   ├── app.py                  # Flask 服務：字幕抓取 + Gemini 摘要
│   └── requirements.txt
├── src/main/java/com/example/youtubesummarizer/
│   ├── controller/
│   │   └── SummaryController.java   # 處理 POST /api/summarize
│   ├── service/
│   │   ├── MediaService.java        # BFF 客戶端：呼叫 media-service、管理快取
│   │   └── YoutubeService.java      # 解析 YouTube URL → videoId
│   ├── config/
│   │   ├── AppConfig.java           # RestTemplate Bean
│   │   └── CacheConfig.java         # Caffeine 快取設定
│   ├── model/
│   │   ├── SummaryRequest.java      # { url }
│   │   └── SummaryResponse.java     # { success, summary / error }
│   └── exception/
│       ├── VideoNotFoundException.java
│       ├── NoSubtitlesException.java
│       └── ServiceUnavailableException.java
├── src/main/resources/
│   ├── application.yml
│   └── templates/index.html         # Thymeleaf 前端頁面
└── pom.xml
```

---

## 注意事項

- 啟動 Spring Boot **之前**，media-service 必須先啟動
- media-service 需要能存取 YouTube（部分地區可能需要 Proxy）
- media-service 支援 AWS Lambda 部署（已整合 [Mangum](https://mangum.fastapiexpert.com/)），Lambda handler 為 `app.handler`
- 字幕長度上限為 30,000 字元，超過部分會被截斷後再送給 Gemini
