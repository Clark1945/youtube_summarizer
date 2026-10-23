import json
import logging
import sys

from daily_digest import config, notify, summarize, youtube

log = logging.getLogger("digest")


def _load_done() -> set[str]:
    if config.STATE_FILE.exists():
        return set(json.loads(config.STATE_FILE.read_text(encoding="utf-8")))
    return set()


def _save_done(done: set[str]) -> None:
    config.STATE_FILE.write_text(json.dumps(sorted(done)), encoding="utf-8")


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    if not config.CHANNEL_URL or not config.GEMINI_API_KEY:
        log.error("請先在 .env 設定 CHANNEL_URL 與 GEMINI_API_KEY")
        return 1

    info = youtube.find_todays_stream()
    if not info:
        log.warning("今天找不到已結束的直播（可能尚未結束或今天沒開）")
        return 2

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
