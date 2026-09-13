#!/usr/bin/env python3
"""Generate SiteCam's short mechanical camera-shutter cue.

The resource is an original deterministic synthesis, made from decaying
transients, mechanism body resonances, and shaped noise. It deliberately
contains two close releases so it reads as a camera shutter (open/close)
instead of a notification beep or a musical tone. No third-party audio is
downloaded or embedded.
"""

from __future__ import annotations

import math
import struct
import wave
from pathlib import Path


SAMPLE_RATE = 44_100
DURATION_SECONDS = 0.285


OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "entry/src/main/resources/rawfile/camera_shutter.wav"
)


def noise(seed: int) -> tuple[float, int]:
    """Return a deterministic bipolar sample and the next generator state."""
    state = (1664525 * seed + 1013904223) & 0xFFFFFFFF
    return ((state / 0xFFFFFFFF) * 2.0 - 1.0), state


def add_click(samples: list[float], start: int, level: float, seed: int) -> int:
    """Add one compact mechanical release and return the updated noise seed."""
    length = int(0.13 * SAMPLE_RATE)
    state = seed
    previous_noise = 0.0
    for index in range(length):
        position = index / SAMPLE_RATE
        # A steep attack followed by a short, non-tonal tail is the audible
        # edge of the shutter blades and latch.
        attack = 1.0 - math.exp(-position * 2600.0)
        tail = math.exp(-position * 42.0)
        high_tail = math.exp(-position * 120.0)
        body = (
            0.62 * math.sin(2.0 * math.pi * 155.0 * position)
            + 0.27 * math.sin(2.0 * math.pi * 285.0 * position + 0.35)
            + 0.12 * math.sin(2.0 * math.pi * 520.0 * position + 1.1)
        ) * tail
        clack = (
            0.44 * math.sin(2.0 * math.pi * 1480.0 * position + 0.2)
            + 0.25 * math.sin(2.0 * math.pi * 3180.0 * position)
            + 0.10 * math.sin(2.0 * math.pi * 6100.0 * position + 0.8)
        ) * high_tail
        value, state = noise(state)
        # High-pass the deterministic noise so it supplies texture without a
        # sustained hiss or a pitched notification-like sound.
        noise_hp = value - previous_noise * 0.88
        previous_noise = value
        texture = noise_hp * math.exp(-position * 155.0)
        envelope = attack * (0.92 * high_tail + 0.18 * tail)
        if start + index < len(samples):
            samples[start + index] += level * (0.52 * body + envelope * (0.58 * clack + 0.17 * texture))
    return state


def main() -> None:
    frame_count = int(DURATION_SECONDS * SAMPLE_RATE)
    samples = [0.0] * frame_count
    seed = 0x53495445  # Stable synthesis; no external source or random file.
    seed = add_click(samples, int(0.012 * SAMPLE_RATE), 1.00, seed)
    add_click(samples, int(0.105 * SAMPLE_RATE), 0.72, seed)

    peak = max(abs(value) for value in samples) or 1.0
    gain = 0.86 / peak
    pcm = b"".join(
        struct.pack("<h", max(-32768, min(32767, int(round(value * gain * 32767.0)))))
        for value in samples
    )
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(OUTPUT), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(SAMPLE_RATE)
        wav.writeframes(pcm)
    print(f"wrote {OUTPUT} ({frame_count / SAMPLE_RATE:.6f}s mono PCM16 {SAMPLE_RATE}Hz)")


if __name__ == "__main__":
    main()
