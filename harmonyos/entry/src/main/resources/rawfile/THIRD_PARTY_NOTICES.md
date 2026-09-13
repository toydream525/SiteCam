# Audio resource notice

## `camera_shutter.wav`

This is an original SiteCam audio cue generated locally by
`harmonyos/scripts/generate_shutter_sound.py`. It is a deterministic synthesis
of short decaying mechanical transients, body resonances, and shaped noise,
with two close releases to suggest a camera shutter opening and closing. It
is not a recording of a particular camera and contains no third-party audio.

The generated resource is mono PCM16 at 44.1 kHz and is approximately 0.285 s
long. SiteCam loads it only as the camera shutter cue after the camera accepts
a photo request; it is not a notification or video microphone track.
