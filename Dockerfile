FROM python:3.13-slim

ENV TZ=Asia/Taipei PYTHONUNBUFFERED=1 DATA_DIR=/data
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg tzdata \
    && rm -rf /var/lib/apt/lists/*
# yt-dlp 解 YouTube JS 驗證用的 runtime
COPY --from=denoland/deno:bin /deno /usr/local/bin/deno

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY main.py .
COPY daily_digest daily_digest

VOLUME /data
CMD ["python", "main.py"]
