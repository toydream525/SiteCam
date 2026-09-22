#!/usr/bin/env python3
"""Prepare SiteCam's camera-shutter cue from a CC0 field recording.

Provenance (verify before changing anything):

  source   : Auslösegeräusch SLR Serienaufnahme bis Puffer voll.oga
  page     : https://commons.wikimedia.org/wiki/File:Ausl%C3%B6seger%C3%A4usch_SLR_Serienaufnahme_bis_Puffer_voll.oga
  author   : Smial
  licence  : CC0 1.0 Universal (Public Domain Dedication)
             https://creativecommons.org/publicdomain/zero/1.0/
  sha256   : d87123b78a552dd31f77a5fc906abc5b4fe943ee01991e14116edff14e6f0055

The recording is a burst of an SLR firing until its buffer fills. This script
takes the final, isolated actuation (nothing follows it, so no neighbouring
shot bleeds into the tail), removes low-frequency handling rumble, trims the
dead air, normalises the peak and applies a short fade. The shipped WAV is
exactly what this script produces, so the asset stays auditable and
reproducible.

Requires ffmpeg only to decode the Vorbis source; all mastering is done here in
plain Python so the result does not depend on an encoder's defaults.

Usage:
  python3 scripts/prepare_shutter_sound.py --source /path/to/slr_buffer.oga [--analyze]
"""

from __future__ import annotations

import argparse
import hashlib
import math
import struct
import subprocess
import tempfile
import wave
from pathlib import Path

SOURCE_URL = (
    "https://upload.wikimedia.org/wikipedia/commons/0/00/"
    "Ausl%C3%B6seger%C3%A4usch_SLR_Serienaufnahme_bis_Puffer_voll.oga"
)
SOURCE_SHA256 = "d87123b78a552dd31f77a5fc906abc5b4fe943ee01991e14116edff14e6f0055"
SOURCE_LICENCE = "CC0 1.0 Universal (Public Domain Dedication) - https://creativecommons.org/publicdomain/zero/1.0/"
SOURCE_AUTHOR = "Smial (Wikimedia Commons)"

# The last actuation of the burst, chosen because the buffer stops there.
CUT_START_SECONDS = 9.640
CUT_LENGTH_SECONDS = 0.220
HIGHPASS_HZ = 60.0
TARGET_PEAK = 0.92
SILENCE_DBFS = -50.0
FADE_SECONDS = 0.006

OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "entry/src/main/resources/rawfile/camera_shutter.wav"
)


def decode(source: Path, rate: int) -> list[float]:
    with tempfile.TemporaryDirectory() as tmp:
        wav = Path(tmp) / "decoded.wav"
        subprocess.run(
            ["ffmpeg", "-v", "error", "-y", "-i", str(source), "-ac", "1", "-ar", str(rate),
             "-c:a", "pcm_s16le", str(wav)],
            check=True,
        )
        return read_wav(wav)


def read_wav(path: Path) -> list[float]:
    with wave.open(str(path), "rb") as handle:
        frames = handle.readframes(handle.getnframes())
    return [value / 32768.0 for value in struct.unpack("<%dh" % (len(frames) // 2), frames)]


def highpass(values: list[float], rate: int, cutoff: float) -> list[float]:
    """Second-order Butterworth high-pass: removes rumble and any DC offset."""
    w0 = 2.0 * math.pi * cutoff / rate
    cos_w0 = math.cos(w0)
    alpha = math.sin(w0) / (2.0 * math.sqrt(2.0))
    b0 = (1.0 + cos_w0) / 2.0
    b1 = -(1.0 + cos_w0)
    b2 = (1.0 + cos_w0) / 2.0
    a0 = 1.0 + alpha
    a1 = -2.0 * cos_w0
    a2 = 1.0 - alpha
    b0, b1, b2, a1, a2 = b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0
    x1 = x2 = y1 = y2 = 0.0
    out: list[float] = []
    for x0 in values:
        y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, x0
        y2, y1 = y1, y0
        out.append(y0)
    return out


def rms(values: list[float]) -> float:
    return math.sqrt(sum(value * value for value in values) / max(1, len(values)))


def master(values: list[float], rate: int) -> list[float]:
    window = int(0.005 * rate)
    threshold = 10.0 ** (SILENCE_DBFS / 20.0)
    end = len(values)
    for index in range(len(values) - window, 0, -window):
        if rms(values[index:index + window]) > threshold:
            end = min(len(values), index + 2 * window)
            break
    values = values[:end]
    peak = max(abs(value) for value in values) or 1.0
    values = [value * TARGET_PEAK / peak for value in values]
    fade = int(FADE_SECONDS * rate)
    for index in range(fade):
        values[len(values) - fade + index] *= 1.0 - index / fade
    return values


def write_wav(values: list[float], rate: int) -> None:
    frames = b"".join(
        struct.pack("<h", max(-32768, min(32767, int(round(value * 32767.0)))))
        for value in values
    )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(frames)


def analyze(values: list[float], rate: int) -> None:
    """Print the transient map so the result can be judged without a speaker."""
    window = int(0.005 * rate)
    print(f"duration={len(values) / rate:.3f}s peak={max(abs(v) for v in values):.3f} rms={rms(values):.4f}")
    for index in range(0, len(values), window):
        chunk = rms(values[index:index + window])
        print(f"  {index / rate * 1000:5.1f} ms {'#' * int(chunk * 240):<52} {chunk:.4f}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, help="path to the downloaded CC0 .oga recording")
    parser.add_argument("--rate", type=int, default=44100)
    parser.add_argument("--analyze", action="store_true")
    args = parser.parse_args()

    source = Path(args.source)
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    if digest != SOURCE_SHA256:
        raise SystemExit(
            f"source checksum mismatch\n  expected {SOURCE_SHA256}\n  actual   {digest}\n"
            f"download {SOURCE_URL}\nlicence: {SOURCE_LICENCE} (author: {SOURCE_AUTHOR})"
        )

    decoded = decode(source, args.rate)
    start = int(CUT_START_SECONDS * args.rate)
    values = decoded[start:start + int(CUT_LENGTH_SECONDS * args.rate)]
    values = highpass(values, args.rate, HIGHPASS_HZ)
    values = master(values, args.rate)
    write_wav(values, args.rate)
    print(f"wrote {OUTPUT} ({len(values) / args.rate:.6f}s mono PCM16 {args.rate}Hz)")
    print(f"source: {SOURCE_AUTHOR} - {SOURCE_LICENCE}")
    if args.analyze:
        analyze(values, args.rate)


if __name__ == "__main__":
    main()
