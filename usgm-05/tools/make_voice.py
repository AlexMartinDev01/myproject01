#!/usr/bin/env python3
"""Offline example Mandarin placeholders. Replace with clear, licensed recordings.
Requires espeak and ffmpeg. Output raw 16 kHz, signed 16-bit, mono little-endian.
The stock eSpeak Mandarin dictionary can be incomplete; quality is not verified.
"""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[1]
AUDIO = ROOT / "firmware/main/assets/audio"
AUDIO.mkdir(parents=True, exist_ok=True)
for name, text in [("small", "小果"), ("medium", "中果"), ("large", "大果"), ("error", "识别异常")]:
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / (name + ".wav")
        subprocess.run(["espeak", "-v", "zh", "-s", "135", "-w", str(wav), text], check=True)
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(wav),
                        "-ar", "16000", "-ac", "1", "-f", "s16le", str(AUDIO / (name + ".raw"))], check=True)
    print(name, (AUDIO / (name + ".raw")).stat().st_size)
