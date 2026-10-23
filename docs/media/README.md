# Glass TV showcase media

All showcase images and the film in this directory were created for ReamyNadzri's Glass TV
with the Brag workflow and HyperFrames 0.8.189. They replace the previous promotional images
and screenshot slideshow.

| File | Content |
|---|---|
| `glass-tv-launch.mp4` | 22-second H.264 film, 1920 x 1080, 30 fps, music and subtle UI sound effects |
| `glass-tv-launch.gif` | Reduced-resolution animated preview for the README |
| `glass-tv-hero.jpg` | Settled Home scene: glass focus, dock, title and creator |
| `glass-tv-focus.jpg` | App zoom and glass focus frame |
| `glass-tv-prayer.jpg` | Six-row Waktu Solat card with illustrative times |
| `glass-tv-settings.jpg` | The app's remote settings and available values |
| `source/` | Editable HTML compositions, local runtime, font and audio assets |
| `share-copy.txt` | A short caption for sharing the project |

The UI is restaged from the app source, including `AppZoom`, `PrayerWidget`, `HomePrefs`, the
settings panel and their strings. These are feature visualizations, not recordings from a TV.
The example tile names, clock and prayer times are illustrative. Users select their own prayer
zone in the installed app.

The Home frame at 1.5 seconds was selected as the poster because it shows the complete dock,
landed focus frame and creator together. It is also baked into the MP4's first frame. The other
stills come from settled feature scenes at 6.3, 8.8 and 16.3 seconds. No earlier photographs,
screenshots or video were used to create the replacement media.

## Credits

- Composition, copy, backgrounds and instrumental score: newly authored for this project.
  The music was synthesized at 110 BPM without sampled music. No narration is included.
- Outfit: the project's font, under the [SIL Open Font License](../../launcher/FONT-LICENSE-OFL.txt).
- UI labels and Android vector icon paths: this project's app sources; original project credits
  and [MIT license](../../LICENSE) remain intact.
- GSAP 3.15.0: local animation runtime, with its license header retained in `source/assets/vendor/`.
- Interface sound effects: Kenney, CC0, from the installed Brag sound library:
  `impactSoft_medium_001.ogg`, `click_003.ogg` and `click2.ogg`.
  See [Kenney assets](https://kenney.nl/assets).
- Production workflow: [Brag](https://github.com/latent-spaces/brag) and
  [HyperFrames](https://github.com/heygen-com/hyperframes).

## Recreate the film

Install Node.js 22+ and FFmpeg, then open `source/` and run:

```bash
npx --yes hyperframes@0.8.189 check
npx --yes hyperframes@0.8.189 preview --background
npx --yes hyperframes@0.8.189 render --quality delivery --fps 30 --output ../glass-tv-launch.mp4
```

The editable composition and local assets are included. Rendering reproduces the film; the
delivery export additionally uses the chosen Home poster for frame zero. Font and sound
licenses are separate from the original visual and music composition.
