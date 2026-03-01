# YouTube Summarizer

輸入 YouTube 網址，自動取得字幕並用 Gemini AI 生成重點摘要，支援多語言輸出。

## 架構

```
Browser
  │  GET  /                          → Thymeleaf 頁面（index.html）
  │  POST /api/summarize { url }
  ▼
Spring Boot :8080
  │  Step 1：GET  /transcript?videoId=xxx   ← 取字幕
  │  Step 2：POST /summarize { transcript, language }  ← 生成摘要
  ▼
media-service :5001  (Python Flask)
  └── YouTube Transcript API → Google Gemini API
```

Spring Boot 僅作 BFF（Backend for Frontend），**不含任何 AI 邏輯**。
media-service 是無狀態的 Flask 服務，負責字幕抓取與 Gemini 呼叫。

---

## 技術棧

| 層級 | 技術 |
|------|------|
| 前端 | Thymeleaf（SSR）、Vanilla JS |
| BFF | Java 17、Spring Boot 3.2.3 |
| media-service | Python 3、Flask、youtube-transcript-api、google-genai |
| AI | Google Gemini 2.5 Flash |

---

## 快速開始

### 前置需求

- Java 17+、Maven
- Python 3.9+
- Google Gemini API 金鑰（[取得](https://aistudio.google.com/apikey)）

### 1. 啟動 media-service（Python）

```bash
cd media-service
pip install -r requirements.txt

# macOS / Linux
export GEMINI_API_KEY=你的金鑰
# Windows
set GEMINI_API_KEY=你的金鑰

python app.py
# 服務跑在 http://localhost:5001
```

確認服務正常：

```bash
curl http://localhost:5001/health
# {"gemini_ready": true, "status": "ok"}
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
| `WEBSHARE_USERNAME` | （選填）| AWS 部署時繞過 YouTube 封鎖用的 Proxy 帳號 |
| `WEBSHARE_PASSWORD` | （選填）| Proxy 密碼 |

### Spring Boot

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `MEDIA_SERVICE_URL` | `http://localhost:5001` | media-service 位址 |

---

## API 文件

### Spring Boot（對外）

#### `GET /`

回傳 Thymeleaf 渲染的前端頁面。

#### `POST /api/summarize`

接收 YouTube 網址，回傳摘要。語言依瀏覽器的 `Accept-Language` 標頭自動判斷。

**Request**
```json
{ "url": "https://www.youtube.com/watch?v=dQw4w9WgXcQ" }
```

**Response 200**
```json
{ "success": true, "summary": "• 重點一\n• 重點二\n...", "errorMessage": null }
```

**Error Response**
```json
{ "success": false, "summary": null, "errorMessage": "錯誤訊息" }
```

| HTTP 狀態碼 | 情境 |
|-------------|------|
| `200` | 成功 |
| `400` | 未提供 URL 或格式錯誤 |
| `404` | 影片不存在或無法存取 |
| `422` | 影片沒有可用字幕 |
| `503` | media-service 連線失敗或 Gemini 錯誤 |

---

### media-service（內部）

#### `GET /transcript?videoId={videoId}`

抓取 YouTube 字幕並回傳純文字。

**Response 200**
```json
{ "transcript": "字幕文字..." }
```

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

#### `GET /health`

確認服務狀態與 Gemini API Key 是否已設定。

**Response 200**
```json
{ "status": "ok", "gemini_ready": true }
```

| HTTP 狀態碼 | 情境 |
|-------------|------|
| `200` | 成功 |
| `400` | 缺少必要參數 |
| `404` | 影片不存在 |
| `422` | 無可用字幕 |
| `503` | Gemini API 錯誤 |

---

## 支援語言

摘要語言依 `Accept-Language` 標頭自動選擇：

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
│   └── requirements.txt        # 本機依賴（不含 serverless-wsgi）
├── src/main/java/com/example/youtubesummarizer/
│   ├── controller/
│   │   ├── PageController.java      # GET  /  → Thymeleaf 頁面
│   │   └── SummaryController.java   # POST /api/summarize
│   ├── service/
│   │   └── MediaService.java        # 呼叫 Python /transcript + /summarize
│   ├── config/
│   │   ├── AppConfig.java           # RestTemplate Bean
│   │   └── CorsConfig.java          # CORS 設定
│   ├── model/
│   │   ├── SummaryRequest.java      # { url }
│   │   └── SummaryResponse.java     # { success, summary, errorMessage }
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

- Spring Boot 啟動**之前**，media-service 必須先啟動
- 本機測試不需要 Webshare Proxy；設定 `WEBSHARE_USERNAME` / `WEBSHARE_PASSWORD` 才會啟用
- media-service 可選擇性部署至 AWS Lambda（需額外安裝 `serverless-wsgi`）
- 字幕長度上限為 30,000 字元，超過部分會被截斷後再送給 Gemini
