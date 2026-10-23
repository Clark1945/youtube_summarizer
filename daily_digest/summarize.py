import time
from pathlib import Path

from google import genai

from . import config

PROMPT = """你是財經資訊整理助理。以下是一場「今日財經」直播的音訊，請用繁體中文整理成方便手機快速閱讀的摘要：

1. 【一句話總結】今天最重要的重點
2. 【大盤與市場】台股／美股／匯率／債券等盤勢重點與數據
3. 【重點個股／產業】提到的股票、產業與主講人的看法（含代號與價位，若有）
4. 【總經與消息面】政策、數據、國際事件
5. 【主講人觀點與操作建議】多空看法、需留意的風險
6. 【待辦／後續觀察】接下來幾天要關注的事件或價位

規則：只根據音訊內容，不要編造；聽不清楚的數字請標註「（不確定）」；不提供投資建議，只轉述主講人說法；總長度控制在 800 字內。"""


def summarize_audio(audio: Path, title: str) -> str:
    client = genai.Client(api_key=config.GEMINI_API_KEY)
    uploaded = client.files.upload(file=str(audio))
    while uploaded.state and uploaded.state.name == "PROCESSING":
        time.sleep(3)
        uploaded = client.files.get(name=uploaded.name)
    try:
        resp = client.models.generate_content(
            model=config.GEMINI_MODEL,
            contents=[uploaded, f"直播標題：{title}\n\n{PROMPT}"],
        )
        return (resp.text or "").strip()
    finally:
        client.files.delete(name=uploaded.name)
