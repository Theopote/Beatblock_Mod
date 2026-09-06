# Environment / VFX

Global track cues compile to typed {@link com.beatblock.timeline.playback.GlobalEventPayload} and execute via {@link com.beatblock.timeline.playback.GlobalEventExecutor}.

## Typed payloads (source of truth after insert)

| Creator kind | Payload | Runtime |
|---|---|---|
| Lighting | `EnvironmentLighting` | Client presentation via `EnvironmentLightingRuntime` (sticky; `transitionSeconds` fade intent) |
| Screen Tint | `ScreenTint` | Screen overlay |
| Weather | `LocalVisualWeather` | Client-only weather presentation |
| Particles | `ParticleBurst` | Particle emit |
| Flash | `ScreenFlash` | Screen flash overlay |
| Audio Mix | `AudioMix` | Stem mixer |

Legacy coarse {@code GlobalEventType} (STAGE / LIGHTING / SCREEN_TINT / SPECIAL) remains for old clips; **Creator UI uses {@link com.beatblock.automap.vfx.GlobalEffectKind}** aligned 1:1 with payloads.

## Creator UI (P2)

- **Category tabs**: Environment Lighting / Screen Tint / Screen Flash / Weather / Particles / Audio — one tab per {@link com.beatblock.automap.vfx.GlobalEffectKind} (no vague shared "Lighting")
- **Musical duration**: Seconds / Beats / Bars for duration, weather transition, and audio fade (timeline still stores seconds)
- **Particle position**: Creator resolves anchor by priority — selected StageObject center → selected BuildLayer center → crosshair / look point → manual XYZ. UI shows `Position: Main Building center` instead of raw coordinates by default.
- **Effect scope**: Creator and Properties show a short scope hint derived from payload type (e.g. Screen Tint → `Screen`, Environment Lighting → `Environment`). No separate scope field on the payload.

## Properties editor (payload-driven)

`GlobalPropertyEditor` decodes the event payload and switches on the sealed {@link GlobalEventPayload} type — no unified kind combo. Each variant renders only its fields (e.g. Screen Tint: Color → Intensity → Duration). Scope labels come from payload type via {@link com.beatblock.automap.vfx.GlobalEffectPayloadUi} (not a stored field).

| Payload | Scope hint |
|---|---|
| `EnvironmentLighting` | Environment |
| `ScreenTint` / `ScreenFlash` | Screen |
| `LocalVisualWeather` | Client |
| `ParticleBurst` | World Position |
| `AudioMix` | Audio |

Particle payload fields:

| Field | Role |
|---|---|
| `x`, `y`, `z` | Baked anchor (subject center at insert, or custom position) |
| `followSubjectKind` | Optional `CameraSubjectKind` |
| `followSubjectRef` | Stage object id when kind is `STAGE_OBJECT` |

## Creator path (one-way)

```
Environment & VFX Panel
        ↓
GlobalEventCreationRequest (time + typed payload)
        ↓
GlobalEventTimelineWriter + CreateGlobalEventCommand
        ↓
Timeline GLOBAL clip/event   ← source of truth after write
```

Insert lifecycle (same as Camera Creator):

- Command → one Undo
- Auto-select clip + event
- Open Timeline Properties
- `TimelineDocumentChangeNotifier`

Properties edits use {@link com.beatblock.timeline.playback.GlobalEventPayloadCodec} encode/decode — **do not** overwrite params with coarse `{type,name}` only.

## Seek reconstruction (semantics)

{@link com.beatblock.automap.vfx.GlobalEffectSemantics}:

| Kind | Examples | Seek behavior |
|---|---|---|
| **CONTINUOUS_STATE** | EnvironmentLighting, Weather, AudioMix, EnvironmentReset | Sticky last-writer; reconstruct |
| **FINITE_ENVELOPE** | ScreenTint, ScreenFlash | Active in `[start, start+duration)`; reconstruct mid-envelope |
| **IMPULSE** | ParticleBurst | Never re-fire historical cues |

`EnvironmentLighting.transitionSeconds` is fade/transition intent (like Weather), **not** an active lifetime.

## Environment lighting runtime

```
GlobalEventPayload.EnvironmentLighting
        ↓
EnvironmentLightingRuntime (client presentation state)
        ↓
soft ambient overlay (does not mutate skylight / block light)
```

Stop / seek-with-no-active-lighting → neutral. Preset **Environment Reset** inserts `EnvironmentReset` (one Undo) and clears lighting / weather / tint / audio mix sticky state.

`PlaybackEngine.seek(RECONSTRUCT_STATE)` advances past all globals ≤ t but only dispatches cues that are still active (`GlobalEffectActiveWindow`). Sample-at-time resolve: `ActiveGlobalEffectState.resolve(events, t)` (tint + flash for export/scrub). Driver clears overlays before reconstruct and syncs tint/flash envelopes on each formal tick.

Examples:

- Screen Tint @10s duration 10s → seek 15s shows tint
- Screen Flash @10s duration 1s → seek 10.5s shows mid-flash alpha
- Particle Burst @12s → seek 15s does **not** re-emit particles

Regression coverage: `ActiveGlobalEffectStateTest`, `PlaybackEngineTest` (tint/flash/particle/weather reconstruct), `GlobalScreenEffectAppearanceParityTest` (runtime/export), `TimelineDocumentChangeNotifierTest` (Creator insert → hot reload).

## Export / Runtime shared payload semantic

```
CompiledGlobalEvent
        ↓
GlobalEventPayload
        ↓
├─ Runtime Backend  (GlobalVisualEffectOverlay + ActiveGlobalEffectState)
└─ Export Backend   (GlobalVisualEffectFrameCompositor + ExportVfxState)
```

Both backends must consume typed payload only — **not** re-interpret raw Timeline parameter maps.

Shared screen appearance (alpha/color formulas): {@link com.beatblock.automap.vfx.GlobalScreenEffectAppearance}
Active selection at time `t`: {@link com.beatblock.automap.vfx.ActiveGlobalEffectState}

## Environment presets (one Undo)

UI may present looks such as Night Performance / Storm / Warm Sunset / Concert Flash.
A preset is **not** one Timeline event — it expands to multiple typed payloads
(e.g. Lighting + Weather + Screen Tint).

```
Apply Storm
  → ApplyEnvironmentPresetCommand
      → CompositeCommand
          → CreateGlobalEventCommand × N
  → one Ctrl+Z undoes all N cues
```

Gateway: {@link com.beatblock.automap.vfx.GlobalEventInsertionService#applyPreset}.
Catalog: {@link com.beatblock.automap.vfx.EnvironmentPreset}.

## Related code

- `com.beatblock.automap.vfx.*`
- `com.beatblock.timeline.playback.GlobalEventPayload`
- `com.beatblock.timeline.playback.GlobalEventPayloadCodec`
- `com.beatblock.timeline.command.ApplyEnvironmentPresetCommand`
- `com.beatblock.client.export.GlobalVisualEffectFrameCompositor`
- `com.beatblock.ui.panels.VfxCreatorPanel`
