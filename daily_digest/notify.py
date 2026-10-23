import smtplib
from email.message import EmailMessage

import requests

from . import config

TELEGRAM_LIMIT = 4000


def send_telegram(text: str) -> None:
    if not (config.TELEGRAM_BOT_TOKEN and config.TELEGRAM_CHAT_ID):
        return
    url = f"https://api.telegram.org/bot{config.TELEGRAM_BOT_TOKEN}/sendMessage"
    for i in range(0, len(text), TELEGRAM_LIMIT):
        r = requests.post(
            url,
            json={"chat_id": config.TELEGRAM_CHAT_ID, "text": text[i:i + TELEGRAM_LIMIT],
                  "disable_web_page_preview": True},
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
    with smtplib.SMTP_SSL(config.SMTP_HOST, config.SMTP_PORT, timeout=30) as smtp:
        smtp.login(config.SMTP_USER, config.SMTP_PASSWORD)
        smtp.send_message(msg)
