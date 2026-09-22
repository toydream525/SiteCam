# Third-party notices

## `camera_shutter.wav` — CC0 field recording

The shutter cue is a real SLR shutter recording, cut and mastered by
`harmonyos/scripts/prepare_shutter_sound.py`.

- Source: *Auslösegeräusch SLR Serienaufnahme bis Puffer voll.oga*
  <https://commons.wikimedia.org/wiki/File:Ausl%C3%B6seger%C3%A4usch_SLR_Serienaufnahme_bis_Puffer_voll.oga>
- Author: Smial (Wikimedia Commons)
- Licence: CC0 1.0 Universal (Public Domain Dedication)
  <https://creativecommons.org/publicdomain/zero/1.0/>
- Source SHA-256: `d87123b78a552dd31f77a5fc906abc5b4fe943ee01991e14116edff14e6f0055`

CC0 dedicates the work to the public domain, so no attribution is legally
required; it is recorded here so the asset can be audited and reproduced. Only
the final, isolated actuation of the source burst is used (starting at 9.640 s
for 0.220 s, chosen because the camera's buffer stops there and no neighbouring
shot bleeds into the tail). It is high-passed at 60 Hz, trimmed below
-50 dBFS, peak-normalised to 0.92 and faded out over 6 ms. The packaged file is
mono PCM16 44.1 kHz, 0.135 s long. SiteCam plays it only as the camera shutter
cue after the camera accepts a photo request; it is not a notification, alarm
or video microphone track.
