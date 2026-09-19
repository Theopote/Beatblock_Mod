# Generated Content Lifecycle

Machine-generated and human-edited Timeline content must coexist safely under regenerate.

## Event origin

`TimelineEventOrigin`:

| Origin | Replaceable by AutoMap? |
|--------|-------------------------|
| `GENERATED` | Yes |
| `USER_EDITED` | No (keeps generator provenance) |
| `MANUAL` | No |
| `IMPORTED` | No |

`eventLocked=true` is an independent flag (not an origin). Locked clips are never removed by replace policies.

Helpers: `TimelineEventOwnership.promoteOnUserEdit` / `detach` / `setLocked`.

User edits via `UpdateAnimationEventCommand`, `MoveEventCommand`, and `ApplyClipDragCommand` promote `GENERATED` → `USER_EDITED`.

## Build layer ownership

`BuildSequenceCompiler` will not unbind/delete a bound clip that is protected (manual / user-edited / locked). Only replaceable `GENERATED` build clips are swapped on recompile.

## Smart AutoMap transaction

One generate is one Undo step:

1. `GenerationDocumentSnapshot.capture` (core tracks + build-layer tracks + plan metadata + layer binds)
2. `SmartAutoMapEngine.generate`
3. Capture after → `SmartAutoMapGenerateCommand` pushed to `CommandManager`

Entry: `AutoMapSettingsPanelPresenter.generate` when a command manager is available.
