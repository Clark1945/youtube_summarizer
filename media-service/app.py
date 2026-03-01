import os
import re
import logging
from flask import Flask, request, jsonify
from youtube_transcript_api import (
    YouTubeTranscriptApi,
    TranscriptsDisabled,
    NoTranscriptFound,
    VideoUnavailable,
)
from google import genai
from google.genai import types

# serverless_wsgi 只在 Lambda 環境才需要，本機可選
try:
    import serverless_wsgi
    _SERVERLESS = True
except ImportError:
    _SERVERLESS = False

# Webshare proxy 只在 AWS 環境才需要（繞過 YouTube block）
# 本機不需要 proxy，只要有設定環境變數才啟用
_webshare_user = os.environ.get("WEBSHARE_USERNAME")
_webshare_pass = os.environ.get("WEBSHARE_PASSWORD")

if _webshare_user and _webshare_pass:
    from youtube_transcript_api.proxies import WebshareProxyConfig
    ytt_api = YouTubeTranscriptApi(
        proxy_config=WebshareProxyConfig(
            proxy_username=_webshare_user,
            proxy_password=_webshare_pass,
        )
    )
else:
    ytt_api = YouTubeTranscriptApi()

logging.basicConfig(level=logging.INFO)
app = Flask(__name__)

@app.after_request
def add_cors(response):
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Methods"] = "POST, GET, OPTIONS"
    response.headers["Access-Control-Allow-Headers"] = "Content-Type"
    return response


GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
GEMINI_MODEL = os.environ.get("GEMINI_MODEL", "gemini-2.5-flash")
MAX_TRANSCRIPT_LENGTH = 30_000

gemini_client = None
if GEMINI_API_KEY:
    gemini_client = genai.Client(api_key=GEMINI_API_KEY)

LANGUAGE_MAP = {
    "zh-tw": "Traditional Chinese (繁體中文)",
    "zh-hk": "Traditional Chinese (繁體中文)",
    "zh-cn": "Simplified Chinese (简体中文)",
    "zh":    "Chinese (中文)",
    "en":    "English",
    "en-us": "English",
    "en-gb": "English",
    "ja":    "Japanese (日本語)",
    "ko":    "Korean (한국어)",
    "es":    "Spanish (Español)",
    "fr":    "French (Français)",
    "de":    "German (Deutsch)",
    "pt":    "Portuguese (Português)",
    "it":    "Italian (Italiano)",
}


def _fetch_transcript(video_id: str) -> str:
    transcript_list = ytt_api.list(video_id)
    transcript = None
    for t in transcript_list:
        transcript = t
        break
    if transcript is None:
        raise NoTranscriptFound(video_id, [], [])
    entries = transcript.fetch()
    text = " ".join(e.text for e in entries if e.text.strip())
    if not text.strip():
        raise NoTranscriptFound(video_id, [], [])
    app.logger.info(
        f"Fetched transcript for {video_id}, lang={transcript.language_code}, length={len(text)}"
    )
    return text


def _preprocess_transcript(transcript: str) -> str:
    text = re.sub(r"\s+", " ", transcript)
    text = re.sub(r"\[.*?\]", "", text)
    text = re.sub(r"\b(um|uh|like)\b", "", text)
    return text.strip()


def _get_language_specific_instructions(language: str) -> str:
    lang_lower = language.lower()
    if "繁體中文" in lang_lower or "traditional chinese" in lang_lower:
        return "使用台灣慣用語，保持專業但平易近人的語氣。"
    if "日本語" in lang_lower or "japanese" in lang_lower:
        return "丁寧語を使用し、分かりやすく説明してください。"
    return "Use clear, professional language."


def _build_prompt(transcript: str, language: str) -> str:
    cleaned = _preprocess_transcript(transcript)
    if len(cleaned) > MAX_TRANSCRIPT_LENGTH:
        cleaned = cleaned[:MAX_TRANSCRIPT_LENGTH] + "..."

    word_count = len(cleaned.split())
    is_long = word_count > 2000
    detail = "comprehensive" if is_long else "concise"
    max_bullets = 12 if is_long else 8
    lang_instructions = _get_language_specific_instructions(language)

    return f"""You are an expert at summarizing video content. Create a {detail} summary of this YouTube video in {language}.

ANALYSIS GUIDELINES:
• Identify 3-5 main themes or topics
• Extract specific examples, data, or quotes
• Note any actionable advice or key insights
• Maintain chronological or logical flow

FORMAT REQUIREMENTS:
• Use • for main bullet points
• Use - for sub-points (supporting details)
• Maximum {max_bullets} main bullets
• Each bullet: 1-2 sentences, clear and specific
• Group related points together

QUALITY STANDARDS:
✓ No redundancy - each point adds unique value
✓ Specific over general - include names, numbers, examples
✓ Actionable over abstract - prefer concrete takeaways
✓ Clear language - explain jargon if used

OUTPUT:
Provide ONLY the formatted bullet-point summary.
No introduction, no conclusion, no meta-commentary.

{lang_instructions}

Transcript:
{cleaned}
"""


def _call_gemini(prompt: str) -> str:
    if not GEMINI_API_KEY or gemini_client is None:
        raise RuntimeError("GEMINI_API_KEY is not set")
    response = gemini_client.models.generate_content(
        model=GEMINI_MODEL,
        contents=prompt,
        config=types.GenerateContentConfig(
            temperature=0.3,
            max_output_tokens=2048,
        ),
    )
    return response.text


# ── Endpoints ────────────────────────────────────────────────────────────────

@app.route("/summarize", methods=["POST", "OPTIONS"])
def summarize():
    """接收 transcript + language，呼叫 Gemini 回傳 summary。"""
    if request.method == "OPTIONS":
        return "", 204

    data = request.get_json(silent=True) or {}
    transcript = (data.get("transcript") or "").strip()
    language = (data.get("language") or "English").strip()

    if not transcript:
        return jsonify({"error": "transcript is required"}), 400

    try:
        prompt = _build_prompt(transcript, language)
        summary = _call_gemini(prompt)
        app.logger.info(f"Summary generated, language={language}, transcript_len={len(transcript)}")
        return jsonify({"summary": summary})

    except RuntimeError as e:
        app.logger.error(f"Gemini config error: {e}")
        return jsonify({"error": "Gemini API unavailable"}), 503
    except Exception as e:
        app.logger.error(f"Unexpected error during summarize: {e}")
        return jsonify({"error": "Gemini API unavailable"}), 503


@app.route("/transcript")
def get_transcript():
    """Debug 用：只取 transcript，不做摘要。"""
    video_id = request.args.get("videoId", "").strip()
    if not video_id:
        return jsonify({"error": "videoId is required"}), 400

    try:
        text = _fetch_transcript(video_id)
        return jsonify({"transcript": text})

    except VideoUnavailable:
        return jsonify({"error": "Video not found or unavailable"}), 404
    except (TranscriptsDisabled, NoTranscriptFound):
        return jsonify({"error": "No transcript available for this video"}), 422
    except Exception as e:
        app.logger.error(f"Unexpected error for {video_id}: {e}")
        return jsonify({"error": str(e)}), 503


@app.route("/health")
def health():
    return jsonify({"status": "ok", "gemini_ready": gemini_client is not None})


# Lambda handler（只有在 serverless_wsgi 安裝時才定義）
if _SERVERLESS:
    def handler(event, context):
        return serverless_wsgi.handle_request(app, event, context)


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5001, debug=True)
