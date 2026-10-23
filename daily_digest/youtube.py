from datetime import datetime, timedelta, timezone
from pathlib import Path

import yt_dlp

from . import config

TZ = timezone(timedelta(hours=config.TIMEZONE_OFFSET_HOURS))


def _streams_url(channel_url: str) -> str:
    base = channel_url.rstrip("/")
    return base if base.endswith("/streams") else base + "/streams"


def _video_date(info: dict) -> datetime | None:
    ts = info.get("release_timestamp") or info.get("timestamp")
    return datetime.fromtimestamp(ts, TZ) if ts else None


def find_todays_stream() -> dict | None:
    """回傳今天（台灣時間）已結束的直播資訊；找不到則回傳 None。"""
    today = datetime.now(TZ).date()
    with yt_dlp.YoutubeDL({"quiet": True, "extract_flat": True, "playlistend": 8}) as ydl:
        entries = ydl.extract_info(_streams_url(config.CHANNEL_URL), download=False).get("entries") or []

    with yt_dlp.YoutubeDL({"quiet": True, "skip_download": True}) as ydl:
        for entry in entries:
            info = ydl.extract_info(entry["url"], download=False)
            if info.get("live_status") != "was_live":
                continue
            started = _video_date(info)
            if not started or started.date() != today:
                continue
            if config.TITLE_KEYWORD and config.TITLE_KEYWORD not in info.get("title", ""):
                continue
            return info
    return None


def download_audio(video_id: str) -> Path:
    """下載音訊並轉成低位元率單聲道 mp3（語音摘要夠用，檔案小）。"""
    config.WORK_DIR.mkdir(exist_ok=True)
    out = config.WORK_DIR / f"{video_id}.%(ext)s"
    opts = {
        "quiet": True,
        "format": "bestaudio/best",
        "outtmpl": str(out),
        "postprocessors": [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "48"}],
        "postprocessor_args": {"extractaudio": ["-ac", "1"]},
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        ydl.download([f"https://www.youtube.com/watch?v={video_id}"])
    return config.WORK_DIR / f"{video_id}.mp3"
