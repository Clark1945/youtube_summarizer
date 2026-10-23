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


READY, PENDING, NONE = "ready", "pending", "none"


def find_todays_stream() -> tuple[str, dict | None]:
    """查今天（台灣時間）的直播狀態。

    READY   已結束、可下載（info 為影片資訊）
    PENDING 直播中／尚未開始／剛結束處理中，需要稍後再查
    NONE    今天沒有符合的直播
    """
    today = datetime.now(TZ).date()
    with yt_dlp.YoutubeDL({"quiet": True, "noprogress": True, "extract_flat": True, "playlistend": 8}) as ydl:
        entries = ydl.extract_info(_streams_url(config.CHANNEL_URL), download=False).get("entries") or []

    pending = False
    with yt_dlp.YoutubeDL({"quiet": True, "skip_download": True, "remote_components": ["ejs:github"]}) as ydl:
        for entry in entries:
            try:
                info = ydl.extract_info(entry["url"], download=False)
            except yt_dlp.utils.DownloadError as e:
                # 預告中的直播無法取得資訊，視為「尚未開始」
                if "will begin" in str(e) or "Premieres" in str(e):
                    pending = True
                continue
            if config.TITLE_KEYWORD and config.TITLE_KEYWORD not in info.get("title", ""):
                continue
            status = info.get("live_status")
            if status == "is_live":
                return PENDING, info
            started = _video_date(info)
            if not started or started.date() != today:
                continue
            if status == "was_live":
                return READY, info
            if status in ("post_live", "is_upcoming"):
                pending = True
    return (PENDING, None) if pending else (NONE, None)


def download_audio(video_id: str) -> Path:
    """下載音訊並轉成低位元率單聲道 mp3（語音摘要夠用，檔案小）。"""
    config.WORK_DIR.mkdir(exist_ok=True)
    out = config.WORK_DIR / f"{video_id}.%(ext)s"
    opts = {
        "quiet": True,
        "noprogress": True,
        "remote_components": ["ejs:github"],
        "format": "bestaudio/best",
        "outtmpl": str(out),
        "postprocessors": [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "48"}],
        "postprocessor_args": {"extractaudio": ["-ac", "1"]},
    }
    with yt_dlp.YoutubeDL(opts) as ydl:
        ydl.download([f"https://www.youtube.com/watch?v={video_id}"])
    return config.WORK_DIR / f"{video_id}.mp3"
