# Timeline Compiler 2.0 — Phase A (Performance check)

## Pipeline

```
Timeline (editable)
      ↓
TimelineValidator.validate(...)
      ↓
TimelineValidationReport
  · counts (animation / camera / layers / markers)
  · ERROR / WARNING / INFO diagnostics
      ↓
PerformanceCheckController.gatePlay(...)
  · errors → block play + open dialog
  · warnings → play + open dialog
  · clean → play immediately
      ↓
PlaybackSession.play + TimelineCompiler.compile (existing)
```

## Rules (Phase A)

| Rule id | Severity | Notes |
|---------|----------|--------|
| `duplicate_event_id` | ERROR | Same `TimelineEvent` id twice |
| `invalid_duration` | ERROR | ≤0 / NaN / Inf |
| `missing_animation_preset` | ERROR | Empty or unknown library id |
| `unsupported_payload` | ERROR | Corrupt / unloadable payload |
| `unbound_target` | WARNING | Aligns with UNBOUND drag UX |
| `missing_stage_object` | WARNING | Target id not registered |
| `event_outside_timeline` | WARNING | time &lt; 0 or &gt; duration |
| `missing_audio_asset` | WARNING | Events present but no `audioPath` |
| count_* | INFO | Summary lines for the UI |

## UI

- Transport: **✓** button → `runPerformanceCheck()`
- Play / hotkey: gated via `PerformanceCheckController`
- Modal: counts + View Problems + Force Play (errors only)

## Next (Phase B)

See [timeline-compiler-2-phase-b.md](timeline-compiler-2-phase-b.md) — **done**:
build layers, audio refs, markers, validation report attached at compile.
