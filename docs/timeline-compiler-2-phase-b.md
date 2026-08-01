# Timeline Compiler 2.0 — Phase B

## Goal

Expand the compiled play program beyond stage + camera freeze:

```
TimelineDocument
      ↓
TimelineValidator  (Phase A, re-run at compile)
      ↓
TimelineCompiler
      ↓
CompiledTimelineSnapshot  ≈ CompiledPerformance
├── stageEvents / compiledStageEvents
├── cameraTrack
├── buildLayers          ← NEW
├── markers              ← NEW
├── audio reference      ← NEW
├── referenceBeatTimes / bpm / duration
├── restoreWorldMutations / sourceGeneration
└── validationReport     ← NEW (attached at compile)
```

## New types

| Type | Role |
|------|------|
| `CompiledBuildLayer` | layer id, stageObjectId, bound clip, visibility, blocks |
| `CompiledMarker` | id, time, name, type |
| `CompiledAudioReference` | path, pathPresent, fileExists, duration, assetId |

## Compile API

```java
TimelineCompiler.compile(timeline);
TimelineCompiler.compile(timeline, engine);
TimelineCompiler.compile(timeline, engine, layerManager); // preferred on Play
```

`BeatBlockClientDriver.startDriving` uses the full three-arg form.

## Validation additions (Phase B)

| Rule | Severity |
|------|----------|
| `audio_file_missing` | WARNING — path set but file not on disk |
| `missing_build_layer` | WARNING — BUILD payload layerId not in manager |

## Isolation guarantees

- Markers sorted by time; list is immutable
- Build layer block lists are copied at compile; live manager dissolve does not shrink snapshot
- Audio path existence checked once at compile (not every tick)

## Phase C (next)

- Dedicated `PlaybackEngine` consuming only `CompiledTimelineSnapshot`
- Optional VFX event track compile
- Problem list jump-to-event from Performance check UI
