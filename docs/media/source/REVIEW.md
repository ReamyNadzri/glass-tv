# Glass TV film review

The film shows the source-faithful dock and glass focus, the app-opening card, the six-row prayer
widget and top-bar option, the eight real settings rows, and a final prayer strip and wordmark.
The chosen direction is a restrained source-based product film. No earlier screenshots, photos
or video are used; app tile names and prayer times are illustrative.

Measured: HyperFrames 0.8.189 full check passed with zero errors and zero warnings in lint,
runtime, layout, motion, contrast and craft. There are two informational text-overlap findings
during brief value and end-card transitions. These do not persist in settled frames.

Reviewed: all five scene beat frames, all four seam strips and the 360px phone contact sheet.
New media is readable and the glass frame, prayer card and settings match the app source.
The end card is held for over two seconds. The font URL and repeated GSAP seek baselines were
fixed after the first check; the duplicate app tile layer was removed; the final home state was
lengthened to satisfy reading time.

The strongest standalone poster is the settled Home frame at 1.5s: title, creator and complete
dock are visible together after the selection lands. Use that frame for the hero and frame zero.
Feature stills: app focus at 6.3s, prayer card at 8.8s, settings at 16.3s.

The weakest element is the short simulated app opening, which deliberately contains no fake
streaming content. The remaining dark glass material is part of the product. No decorative
audio visualization is added. Sound is measured from the rendered file, not judged by listening.

Final export: 22.0 seconds, 660 frames, 1920x1080 at30fps, H.264 with AAC audio. Full FFmpeg decoding passed. Poster frame is baked into frame zero; source fonts and SFX credits are included.
