# Timeline Compiler 2.0 — Phase C

## Goal

Formal play consumes **only** a compiled program; preview may still read the live document.

```
Play
  → Validator (Phase A)
  → TimelineCompiler (Phase B)
  → PlaybackEngine.load(snapshot)
  → each tick: PlaybackEngine.advance(time, stageHandler, globalHandler)
```

## PlaybackEngine

| API | Role |
|-----|------|
| `load(CompiledTimelineSnapshot)` | Index stage events by id; reset cursors |
| `advance(time, stageHandler, globalHandler)` | Dual-pointer over sorted stage + global lists |
| `findCompiledStage(eventId)` | O(1) lookup for dispatch |
| Rewind | `time < lastTime` clears scheduled sets + cursors |

`BeatBlockClientDriver`:

- **Formal play** (`driving=true`): only `PlaybackEngine`
- **Preview** (`driving=false`): live `Timeline.getStageEvents()` dual-pointer (edit-friendly)

## Compiled global / VFX cues

`CompiledGlobalEvent` list on the snapshot (from global track).  
Handlers receive lighting/special cues without re-reading the document.

## Performance check → Jump

Problem list rows have a **跳转 / Jump** button:

1. `PerformanceCheckController.requestJumpTo(eventId, time)`
2. Toolbar each frame: `transport.consumePerformanceCheckJump(editor)`
3. Seeks playhead, centers view, selects event

## Tests

`PlaybackEngineTest`: due-event advance, rewind, isolation from live edits, global cues, id lookup.

## Follow-ups (post Phase C)

### Done in continuation
- **Formal camera isolation**: while `BeatBlockClientDriver.isDriving()`, camera samples **only** `compiledPlayback.cameraTrack()` (never live Timeline)
- **Export camera**: `sampleAtExportTime` uses active compiled snapshot or one-shot `TimelineCompiler.compile`
- **Problem filters**: All / Errors / Warnings in Performance check dialog
- **Jump**: seek + center view + select event (Phase C)

### Still open
- Richer particle VFX program (beyond global track cues)
- Camera evaluation fully owned by `PlaybackEngine` (currently controller still samples track)
- Multi-select / batch fix from problem list
