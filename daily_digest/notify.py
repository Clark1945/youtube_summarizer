import html
import re
import smtplib
from email.message import EmailMessage

import markdown
import requests

from . import config

TELEGRAM_LIMIT = 4000


def _to_telegram_html(text: str) -> str:
    """把常見 Markdown（標題、粗體、清單符號）轉成 Telegram 支援的 HTML 子集。"""
    text = html.escape(text, quote=False)
    text = re.sub(r"^#{1,6}\s*(.+)$", r"<b>\1</b>", text, flags=re.M)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    return re.sub(r"^(\s*)[*-]\s+", r"\1• ", text, flags=re.M)


def _to_email_html(text: str) -> str:
    # python-markdown 要求清單前有空行，否則會當成一般段落
    lines, prev_ok = [], True
    for line in text.split("\n"):
        is_list = bool(re.match(r"\s*[*-]\s", line))
        if is_list and not prev_ok:
            lines.append("")
        lines.append(line)
        prev_ok = is_list or not line.strip()
    return markdown.markdown("\n".join(lines), extensions=["nl2br"])


def send_telegram(text: str) -> None:
    if not (config.TELEGRAM_BOT_TOKEN and config.TELEGRAM_CHAT_ID):
        return
    url = f"https://api.telegram.org/bot{config.TELEGRAM_BOT_TOKEN}/sendMessage"
    text = _to_telegram_html(text)
    for i in range(0, len(text), TELEGRAM_LIMIT):
        r = requests.post(
            url,
            json={"chat_id": config.TELEGRAM_CHAT_ID, "text": text[i:i + TELEGRAM_LIMIT],
                  "parse_mode": "HTML", "disable_web_page_preview": True},
            timeout=30,
        )
        r.raise_for_status()


def send_email(subject: str, body: str) -> None:
    if not (config.SMTP_USER and config.SMTP_PASSWORD and config.EMAIL_TO):
        return
    msg = EmailMessage()
    msg["From"] = config.SMTP_USER
    msg["To"] = config.EMAIL_TO
    msg["Subject"] = subject
    msg.set_content(body)
    msg.add_alternative(_to_email_html(body), subtype="html")
    with smtplib.SMTP_SSL(config.SMTP_HOST, config.SMTP_PORT, timeout=30) as smtp:
        smtp.login(config.SMTP_USER, config.SMTP_PASSWORD)
        smtp.send_message(msg)
