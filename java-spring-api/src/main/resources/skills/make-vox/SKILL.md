---
name: make-vox
description: >-
  Build a Vox/Johnny-Harris-style layered-collage documentary video end to end:
  researched script, provisional scene and layer plan, native-duration voice lock,
  economical image/HTML/SVG layer production, choreographed TSX assembly on one
  oversized CollageBoard camera, word-synced captions, frame QA, final render, and
  optional sparse editorial SFX. Use for Vox-style documentary, history, map-travel,
  archival, education, economics, or technology explainers. This is a visual language
  that supports shorts, long-form videos, segments, vertical, or horizontal output;
  it is not plain TSX animation (make-short) or video-model footage (make-ai-short).
  Defers raw TSX crash rules to vidtsx-2d-generator and SFX taste to suggest-sfx.
disabled: true
---

# make-vox — layered-collage documentary production

Read `D:\Open Source\claude-faceless-shorts-creator\skills\VOX_DESIGN.md` first. That file is the
canonical visual and architectural contract. This skill turns it into an executable production
workflow.

The result is a composed paper world, not a slideshow: every scene is dissected into layers
(paper board, cutout subjects, maps, archival photos, labels, arrows, routes, and data/UI pieces),
then assembled through many simple, staggered movements and a traveling virtual camera. Avoid
complex per-image animation; polish comes from composition, choreography, depth, timing, and
editorial restraint.

## Non-negotiable visual rules

- **Paper world:** use a warm cream/kraft surface, subtle grain, and a soft vignette. Nothing is
  pitch black or pure white; every element should feel placed on a physical surface.
- **Physical layers:** subjects are die-cut photos or illustrations with a subtle white sticker
  edge and soft drop shadow. Archival photos receive a sepia/duotone treatment, white photo
  border, and slight rotation. Documents and screenshots receive a paper-scan treatment.
- **Choreography over effects:** stagger entrances using slide, pop/overshoot, rise, or mask-wipe.
  Every visible layer has slow idle drift/breathing; do not leave the board completely frozen.
- **Camera as narrator:** use one oversized collage board and move the camera between scene anchor
  points with pushes, pans, and settles. Give layers depth so camera motion creates parallax.
- **Annotation grammar:** use slightly rotated white label chips, marker-highlight sweeps, hand-
  drawn arrows that draw on, and dashed travel routes over maps. AI-generated text is not trusted;
  render important text as HTML/SVG/TSX and annotate images with chips instead.
- **Typography and contrast:** headlines use `FONT_EDITORIAL` (Source Serif 4, weight 700).
  Over any busy map or photo, every statement MUST use a cream `backing` strip behind each word;
  marker highlights sit over the strip. Light accent chips require a dark `kickerColor`.
- **Editorial restraint:** transitions and SFX support the explanation. Do not decorate every cue
  or turn the board into a collection of unrelated floating stickers.

## Repository contract

Run commands from `D:\Open Source\claude-faceless-shorts-creator`.

```text
vox-shorts/vox-N-<topic>/
  script.md                         researched script and scene beat sheet
  scenes.json                       provisional, then voice-locked scene contract
  voice/                             generated/approved narration (gitignored as configured)
  sfx-plan.json                     optional SFX plan
  output/                           renders and review artifacts
remotion/src/shots/vox-N/            one registered Remotion composition
remotion/src/lib/collage.tsx        shared CollageBoard and collage primitives
media/projects/vox-N-<topic>/layers generated layers and gen_image sidecars
media/library/                      reusable assets proven across two or more videos
```

Use the shared kit where possible: `CollageBoard`, `Cutout`, `ArchivalPhoto`, `PaperBG`,
`LabelChip`, `SerifStatement`, `MarkerHighlight`, `SketchArrow`, `Grain`, and the `VOX` palette.
Grow the kit generically; keep project-specific hacks inside the shot.

## Stage 1 — research, script, and provisional dissection

1. Verify the factual claims before scripting. Write `script.md` with the narration beats and
   the visual purpose of each scene.
2. Create provisional `scenes.json`. For every scene specify camera anchor `x/y/zoom`, the exact
   layer inventory, each layer's source and entrance, annotations, and provisional timing.
3. A scene normally has 2–6 meaningful layers. If the layer list cannot be named, the scene is
   not designed yet. Decide the complete inventory before generating: AI images, cutouts, HTML
   captures, SVG/TSX shapes, reusable assets, and archival treatments.
4. Prefer reuse before generation. Generic paper textures and cutouts used by two or more videos
   belong in `media/library/`.

Do not generate final artwork or timed animation before the audio clock is locked.

## Stage 2 — voice lock and timing lock

1. Generate narration immediately after the approved script with the `gen_voice` agent tool.
2. Audit pronunciation, pacing, pauses, word timings, and total duration. Approve the untouched
   track before visual production.
3. Never speed up, slow down, trim, cut, pad, or otherwise time-fit narration. Later scene lines
   shift to preserve the natural pauses.
4. Write the real word timings and total duration into `beats.json`/`scenes.json`. From this point
   forward, scene boundaries, camera arrivals, layer entrances, annotation holds, captions, and
   SFX all use this locked audio clock. Convert seconds to frames once using the composition FPS;
   do not hand-tune unrelated frame numbers.

## Stage 3 — economical layer production

Choose the cheapest source that gives the required result:

- Photographic subjects, textures, maps, and archival shots: `gen_image` → `cutout` when isolated.
  Prompt maps and archival images with `NO text, no labels`; AI text is unreliable.
- Cutout subjects: request an isolated subject on a plain white background to improve edge quality.
  Use `rembg` through `cutout`; white-key fallback is only for simple assets and fails on contact
  shadows.
- Charts, UI, documents, and styled text blocks: HTML/CSS/JS → Playwright `capture_web` PNG with
  `omitBackground` when transparency is required.
- Arrows, routes, geometric shapes, and highlights: author SVG directly in TSX. Never rasterize
  these; they are cheaper and remain animatable.

Audit every generated layer before assembly: subject identity, crop, transparency, edge quality,
text legibility, archival treatment, and whether it actually serves the narration.

## Stage 4 — TSX assembly and choreography

- Build one composition per video. Put one oversized `CollageBoard` at the root and put scenes
  inside it as `<Sequence layout="none">` sections. The camera must travel across scene
  boundaries rather than resetting at every scene.
- Camera keyframes are **global** frames. Entrance `at` values inside a Sequence are **local**
  frames. Check this explicitly to avoid the classic local/global frame timing bug.
- Derive all global frames from the voice-locked timeline. Use `frameAt(seconds)` or the existing
  timeline helpers; never invent a second timing clock.
- Keep persistent scenery (such as a map shared by two scenes) in a long Sequence. Give temporary
  annotations shorter Sequences with fade-outs so they clear before the next composition needs
  the space.
- Place large layers with named geometry constants, then derive annotation points as fractions of
  those layers (for example `mapPt(0.24, 0.52)`). This makes asset replacement manageable.
- Use sensible depth: board around `-0.06`, scenery around `0–0.05`, and foreground props around
  `0.08–0.15`. Preserve idle drift on every layer unless it is intentionally a background.
- Use `<Img>`, frame-based monotonic interpolation, and Remotion-safe styles. Follow
  `vidtsx-2d-generator` for crash-proof TSX rules.

## Stage 5 — captions, SFX, QA, and render

Captions are optional for documentary work, but if enabled they must use the real word timings
from `gen_voice`, not estimated line timings. Add SFX/music only after visuals and voice are
locked, using `suggest-sfx`: paper slides on entrances, soft thuds on cutout landings, whooshes
on camera moves, and route-draw scribbles are appropriate. Keep decorative cues sparse and mark
them optional.

Regenerate the registry, render targeted stills, and read every PNG before the full render:

```powershell
Set-Location 'D:\Open Source\claude-faceless-shorts-creator\remotion'
npm run gen
node scripts/frames.mjs VoxNName <frame-0,voice-beats,entrances,camera-arrivals,transitions,last> --scale=0.5
```

Inspect every checkpoint for:

- entrances landing on the intended words and no local/global frame errors;
- camera framing and parallax, with nothing important cropped;
- paper surface, sticker edges, shadows, grain, archival treatment, and idle drift;
- text contrast over its actual background, especially the mandatory `backing` rule;
- clean cutout edges (re-cut the asset instead of masking bad edges in TSX);
- scene handoffs, with annotations gone before the next scene needs that space.

Then render the complete composition and spot-check frames from the MP4. The final render must use
the untouched voice track at its native duration: never use `atempo`, trimming, narration padding,
or any other audio-duration adjustment. Re-render after any timing change.

## Done means

`script.md` is researched and every scene names its layers; `scenes.json` is voice-locked; all
layers and sidecars are audited in `media/projects`; one composition is registered; the CollageBoard
camera, paper world, cutout treatment, idle motion, typography contrast, captions, and SFX have
been QA'd at voice beats and transitions; and the final MP4 has been spot-checked with the native-
duration voice track. Shared kit changes are generic and reusable.