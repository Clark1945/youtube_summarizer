# YouTube Summarizer — 專案文件

## 架構總覽

本專案有兩套並存的架構，共用同一個 Python Lambda。

---

## 架構 A：Java Lambda（現行生產版）

```
Browser
  POST { url }  +  Accept-Language header
    ↓
API Gateway (youtube-summarizer stack)
  /api/summarize
    ↓
Java Lambda  (Spring Boot / aws-serverless-java-container)
  1. extractVideoId(url)          — regex 解析 YouTube URL
  2. resolveLanguage(header)      — Accept-Language → 語言名稱
  3. GET Python API Gateway /transcript?videoId=xxx
  4. POST Gemini REST API → summary
    ↓
{ success, summary, errorMessage }
```

### 元件

| 元件 | 說明 |
|---|---|
| `SummaryController` | 接收請求、解析 URL 與語言、回傳結果 |
| `MediaService` | 呼叫 Python API Gateway 抓字幕 |
| `GeminiService` | 前處理字幕、建 prompt、呼叫 Gemini |

### 環境變數

| 變數 | 說明 |
|---|---|
| `MEDIA_SERVICE_URL` | Python API Gateway base URL |
| `GEMINI_API_KEY` | 從 SSM `/youtube-summarizer/gemini-api-key` 讀取 |
| `GEMINI_MODEL` | 預設 `gemini-2.5-flash` |

### 部署

```bash
sam build && sam deploy   # template.yaml，stack: youtube-summarizer
```

---

## 架構 B：Python-only（輕量版）

```
Browser
  extractVideoId(url)         — JS 處理
  resolveLanguage(navigator.language)  — JS 處理
  POST { videoId, language }
    ↓
API Gateway (sam-app stack)
  /process
    ↓
Python Lambda
  _fetch_transcript(videoId)  — Webshare proxy → YouTube
  _build_prompt(transcript, language)
  _call_gemini(prompt)
    ↓
{ summary }  /  { error }
```

### 元件

| 元件 | 說明 |
|---|---|
| `frontend/index.html` | 純靜態頁面，直接呼叫 Python API Gateway |
| `media-service/app.py` | Flask + Mangum，所有邏輯在 Python |

### 環境變數

| 變數 | 說明 |
|---|---|
| `GEMINI_API_KEY` | 從 SSM `/myapp/gemini_api_key` 讀取 |
| `GEMINI_MODEL` | 預設 `gemini-2.5-flash` |
| `WEBSHARE_USERNAME` | Proxy 帳號，SSM `/myapp/webshare_username` |
| `WEBSHARE_PASSWORD` | Proxy 密碼，SSM `/myapp/webshare_password` |

### 部署

```bash
cd media-service
sam build && sam deploy   # template.yaml，stack: sam-app
```

---

## Python Lambda Endpoints

| Method | Path | 說明 |
|---|---|---|
| `POST` | `/process` | 架構 B 入口：videoId → transcript → summary |
| `GET` | `/transcript` | Debug：回傳原始字幕文字 |
| `POST` | `/summarize` | 舊接口：接受 transcript 文字直接呼叫 Gemini |

---

## 為什麼需要 Python Lambda

YouTube 封鎖 AWS IP，無法直接抓字幕。Python Lambda 透過 **Webshare Proxy** 繞過封鎖。

---

## 基礎設施

| 服務 | 用途 |
|---|---|
| AWS Lambda | Java Lambda（架構 A）、Python Lambda（共用） |
| AWS API Gateway | 兩個 Lambda 各自的 HTTP 入口 |
| AWS SSM Parameter Store | 存放 API key 與 Proxy 帳密 |
| AWS SAM / CloudFormation | 部署管理 |
| AWS CloudWatch | Log 監控 |

---

## 目錄結構

```
├── src/                        Java Lambda 原始碼 (Spring Boot)
│   └── main/java/.../
│       ├── controller/         SummaryController
│       ├── service/            MediaService, GeminiService
│       ├── model/              SummaryRequest, SummaryResponse
│       ├── exception/          VideoNotFoundException, NoSubtitlesException, ServiceUnavailableException
│       └── config/             AppConfig (RestTemplate), CorsConfig
├── media-service/              Python Lambda
│   ├── app.py                  Flask endpoints + Gemini + transcript
│   ├── template.yaml           SAM (stack: sam-app)
│   └── requirements.txt
├── frontend/
│   └── index.html              架構 B 靜態頁面
├── template.yaml               SAM (stack: youtube-summarizer，Java Lambda)
└── samconfig.toml              sam deploy 預設參數
```
