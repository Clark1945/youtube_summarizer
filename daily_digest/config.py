import os
from pathlib import Path

from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parent.parent
load_dotenv(ROOT / ".env")

DATA_DIR = Path(os.getenv("DATA_DIR", ROOT))  # 部署時掛載成 volume，保存已處理紀錄
WORK_DIR = DATA_DIR / "work"
STATE_FILE = DATA_DIR / "state.json"
TIMEZONE_OFFSET_HOURS = 8  # 台灣時間

CHANNEL_URL = os.getenv("CHANNEL_URL", "")
TITLE_KEYWORD = os.getenv("TITLE_KEYWORD", "")

POLL_SECONDS = 60
# 直播中／剛結束時，最多等多久（分鐘）
WAIT_PENDING_MINUTES = int(os.getenv("WAIT_PENDING_MINUTES", "60"))
# 完全找不到今天的直播時，再多等多久才確定「今天沒直播」（分鐘）
WAIT_NONE_MINUTES = int(os.getenv("WAIT_NONE_MINUTES", "10"))
# 今天沒直播時是否仍通知你
NOTIFY_NO_STREAM = os.getenv("NOTIFY_NO_STREAM", "true").lower() == "true"

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")

TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "")
TELEGRAM_CHAT_ID = os.getenv("TELEGRAM_CHAT_ID", "")

SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "465"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASSWORD = os.getenv("SMTP_PASSWORD", "")
EMAIL_TO = os.getenv("EMAIL_TO", "")
