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
| BFF | Java 17、Spring Boot 3.2.3、Caffeine Cache |
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
│   │   ├── CacheConfig.java         # Caffeine 快取設定（transcripts / summaries）
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

## 快取機制

Spring Boot 使用 **Caffeine Cache** 對 Python 的回傳內容做兩層快取，避免重複呼叫：

| 快取名稱 | Cache Key | 快取內容 | TTL | 最大筆數 |
|----------|-----------|---------|-----|---------|
| `transcripts` | `videoId` | Python `/transcript` 回傳的字幕文字 | 1 小時 | 500 |
| `summaries` | `videoId:language` | Python `/summarize` 回傳的摘要 | 1 小時 | 500 |

**快取命中邏輯：**
- 相同 `videoId` 再次請求 → 直接從 `transcripts` 拿字幕，**不呼叫** Python `/transcript`
- 相同 `videoId` + `language` 再次請求 → 直接從 `summaries` 拿摘要，**不呼叫** Python 任何端點
- 錯誤（exception）不會被快取，確保暫時性錯誤可以重試

---

## 注意事項

- Spring Boot 啟動**之前**，media-service 必須先啟動
- 本機測試不需要 Webshare Proxy；設定 `WEBSHARE_USERNAME` / `WEBSHARE_PASSWORD` 才會啟用
- media-service 可選擇性部署至 AWS Lambda（需額外安裝 `serverless-wsgi`）
- 字幕長度上限為 30,000 字元，超過部分會被截斷後再送給 Gemini

### 個人紀錄
剛開始我在本地開發，主要使用Java作為Backend Python作為Worker的形式開發的，我最初預計使用Youtube Data API
去取得字幕，但由於Youtube Data API實際上只能取得自己上傳的影片的字幕，所以我只能放棄Youtube Data API，轉
採用Python第三方套件去取得影片字幕，之後我又申請了Gemini AI API Key並請他總結，完成我的基本版功能。

後來因為我想要實際把服務上線，但是我又不想要付款server費用，所以我就考慮使用AWS Lambda，而這上面我就遇到了相當
多的困難，例如說當前服務必須要修改為AWSLambdaHandler才能使用，Serverless API的冷啟動問題，API Gateway的30
秒timeout長度問題等等。
我的體感是如果是ColdStart且沒有任何其他機制的話常常API call會超過10秒，然後我想要call一隻以上的話就很容易timeout
但是如果都寫在一起的話，又會導致功能強制耦合，破壞我的架構。
現有架構如果想要最佳解的話就是ECS Fargate但是要付錢，否則就要研究其他雲是否有免費instance但是我覺得很難。
總之經過思考與取捨我暫時將這個功能留存在本地中，未來可能
會繼續study這一塊。至於靜態網頁的存放，除了S3以外也可以考慮用Supabase因為supabase目前提供免費而S3要錢。

###

![Demo Image](preivew.png)