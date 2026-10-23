import json
import logging
import sys
import time

from daily_digest import config, notify, summarize, youtube

log = logging.getLogger("digest")


def _load_done() -> set[str]:
    if config.STATE_FILE.exists():
        return set(json.loads(config.STATE_FILE.read_text(encoding="utf-8")))
    return set()


def _save_done(done: set[str]) -> None:
    config.STATE_FILE.write_text(json.dumps(sorted(done)), encoding="utf-8")


def _wait_for_stream() -> tuple[str, dict | None]:
    """輪詢直到直播結束可下載；超過等待上限回傳 None。"""
    start = time.monotonic()
    while True:
        status, info = youtube.find_todays_stream()
        if status == youtube.READY:
            return status, info
        waited = (time.monotonic() - start) / 60
        limit = config.WAIT_PENDING_MINUTES if status == youtube.PENDING else config.WAIT_NONE_MINUTES
        if waited >= limit:
            return status, None
        log.info("狀態：%s，%d 秒後再查", "直播中/處理中" if status == youtube.PENDING else "尚未找到", config.POLL_SECONDS)
        time.sleep(config.POLL_SECONDS)


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if not config.CHANNEL_URL or not config.GEMINI_API_KEY:
        log.error("請先在 .env 設定 CHANNEL_URL 與 GEMINI_API_KEY")
        return 1

    status, info = _wait_for_stream()
    if not info:
        if status == youtube.PENDING:
            msg = f"直播等了 {config.WAIT_PENDING_MINUTES} 分鐘仍未結束，今天先不摘要。"
        else:
            msg = "今天沒有找到直播（可能休息或未開播），不需摘要。"
        log.warning(msg)
        if config.NOTIFY_NO_STREAM:
            notify.send_telegram(msg)
            notify.send_email("今日財經：今天沒有摘要", msg)
        return 0

    vid, title = info["id"], info["title"]
    done = _load_done()
    if vid in done:
        log.info("已處理過 %s，略過", vid)
        return 0

    log.info("下載音訊：%s", title)
    audio = youtube.download_audio(vid)
    log.info("AI 摘要中…")
    summary = summarize.summarize_audio(audio, title)

    url = f"https://www.youtube.com/watch?v={vid}"
    body = f"{title}\n{url}\n\n{summary}"
    notify.send_telegram(body)
    notify.send_email(f"今日財經摘要｜{title}", body)

    audio.unlink(missing_ok=True)
    done.add(vid)
    _save_done(done)
    log.info("完成")
    return 0


if __name__ == "__main__":
    sys.exit(main())
