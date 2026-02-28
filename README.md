# YouTube Summarizer

輸入 YouTube 網址，自動取得字幕並用 Gemini AI 生成重點摘要。

## 架構

```
Browser → Spring Boot (port 8080)
                ├── Python Flask (port 5001)  ← 取 YouTube 字幕
                └── Google Gemini API         ← 生成摘要
```

## 技術棧

| 層級 | 技術 |
|------|------|
| 後端 | Java 17、Spring Boot 3.2.3、Thymeleaf |
| 字幕服務 | Python 3.13、Flask、youtube-transcript-api 1.2.4 |
| AI | Google Gemini 2.5 Flash |
| 快取 | Caffeine Cache |

## 啟動方式

### 1. 啟動 Python 字幕服務

```bash
cd transcript-service
pip install -r requirements.txt
python app.py
# 服務跑在 http://localhost:5001
```

> Windows 若有多個 Python 環境，請指定完整路徑：
> `D:\DevTool\Python\python.exe app.py`

### 2. 啟動 Spring Boot

```bash
mvn spring-boot:run
# 服務跑在 http://localhost:8080
```

### 環境變數（選填）

| 變數 | 預設值 | 說明 |
|------|--------|------|
| `GEMINI_API_KEY` | 內建 key | Google Gemini API 金鑰 |
| `TRANSCRIPT_SERVICE_URL` | `http://localhost:5001` | Python 字幕服務位址 |

## 專案結構

```
youtube-summarizer/
├── src/main/java/com/example/youtubesummarizer/
│   ├── controller/SummaryController.java   # 處理 HTTP 請求
│   ├── service/
│   │   ├── YoutubeService.java             # 解析 URL、呼叫字幕服務
│   │   └── GeminiService.java              # 呼叫 Gemini API 生成摘要
│   ├── exception/                          # 自定義例外
│   ├── model/                              # Request / Response DTO
│   └── config/                             # RestTemplate、Cache 設定
├── src/main/resources/
│   ├── application.yml
│   └── templates/index.html                # Thymeleaf 前端頁面
├── transcript-service/
│   ├── app.py                              # Flask 字幕服務
│   └── requirements.txt
└── pom.xml
```

## API

### 字幕服務（Python）

```
GET http://localhost:5001/transcript?videoId={videoId}
```

回應：
```json
{
  "transcript": "字幕內容...",
  "language": "zh-Hant"
}
```

錯誤碼：`404` 影片不存在、`422` 無字幕、`503` 服務錯誤

## 注意事項

- 啟動 Spring Boot **之前**，Python 字幕服務必須先啟動
- 字幕與摘要結果會被 Caffeine Cache 暫存，相同影片不會重複呼叫 API
- Gemini 模型可透過 `application.yml` 的 `app.gemini.model` 調整
