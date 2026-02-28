from flask import Flask, request, jsonify
from youtube_transcript_api import (
    YouTubeTranscriptApi,
    TranscriptsDisabled,
    NoTranscriptFound,
    VideoUnavailable,
)
import logging

logging.basicConfig(level=logging.INFO)
app = Flask(__name__)


@app.route('/transcript')
def get_transcript():
    video_id = request.args.get('videoId', '').strip()
    if not video_id:
        return jsonify({'error': 'videoId is required'}), 400

    try:
        ytt_api = YouTubeTranscriptApi()
        transcript_list = ytt_api.list(video_id)

        # Pick the first available transcript (manual preferred, then auto-generated)
        transcript = None
        for t in transcript_list:
            transcript = t
            break

        if transcript is None:
            return jsonify({'error': 'No transcript available'}), 422

        entries = transcript.fetch()
        text = ' '.join(e.text for e in entries if e.text.strip())

        if not text.strip():
            return jsonify({'error': 'Transcript is empty'}), 422

        app.logger.info(f'Fetched transcript for {video_id}, lang={transcript.language_code}, length={len(text)}')
        return jsonify({'transcript': text, 'language': transcript.language_code})

    except VideoUnavailable:
        return jsonify({'error': 'Video not found or unavailable'}), 404
    except (TranscriptsDisabled, NoTranscriptFound):
        return jsonify({'error': 'No transcript available for this video'}), 422
    except Exception as e:
        app.logger.error(f'Unexpected error for {video_id}: {e}')
        return jsonify({'error': str(e)}), 503


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=False)
