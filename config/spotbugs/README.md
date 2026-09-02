# SpotBugs policy

BeatBlock tracks SpotBugs findings with a **fingerprint baseline** (`baseline_fingerprints.txt`) and a **count cap** (`baseline.txt`). CI runs `./gradlew spotbugsCheck` on every build.

## Hard rules

1. **No new findings** — Any fingerprint not in `baseline_fingerprints.txt` fails `spotbugsCheck`.
2. **Baseline only burns down** — `spotbugsUpdateBaseline` refuses to raise the count unless `-PspotbugsAllowIncrease` is passed (break-glass only; needs justification in the PR).
3. **Count cap** — Current bug count must not exceed `baseline.txt` (allows fixing without updating baseline until you regenerate).
4. **Milestone caps** — Each release phase has a maximum allowed count (see `milestones.properties`). When `enforcePhaseCap=true`, `spotbugsCheck` fails if the count is above the active milestone.

## Milestones

| Phase | Max fingerprints | Notes |
|-------|------------------|-------|
| Creator Alpha | 180 | Current target phase (`current=creator-alpha`) |
| Beta | 80 | |
| 1.0 | 20 | Remaining items must be reviewed suppressions only |

Historical reference: baseline was **285**, then **254**, **252**, now **120** (trend must continue downward).

## What belongs in baseline vs exclude.xml

- **Fix in code** — Default. Prefer real fixes (NP, concurrency, dead stores).
- **`exclude.xml`** — Documented, reviewed package/class/pattern suppressions (Mixin, ImGui frame state, Gson DTOs, etc.). Each block must have a comment explaining *why*.
- **`baseline_fingerprints.txt`** — Known findings not yet fixed. Treat as debt; remove when fixed. Do not add without attempting a fix first.

Absolute zero is not required at 1.0, but anything left must be either fixed or covered by a **reviewed** `exclude.xml` entry (not an silent baseline addition).

## Commands

```bash
# Analyze (report only)
./gradlew spotbugsMain

# CI gate: no new bugs, count <= baseline, optional phase cap
./gradlew spotbugsCheck

# Regenerate baseline after fixing bugs (refuses to increase count)
./gradlew spotbugsUpdateBaseline

# Break-glass only — document in PR
./gradlew spotbugsUpdateBaseline -PspotbugsAllowIncrease
```

## Updating baseline after a burn-down

1. Fix findings in code (or add a reviewed `exclude.xml` rule if truly intentional).
2. Run `./gradlew spotbugsMain` and inspect `build/reports/spotbugs/main/index.html`.
3. Run `./gradlew spotbugsUpdateBaseline` — count must be **≤** previous `baseline.txt`.
4. Commit `baseline.txt`, `baseline_fingerprints.txt`, and code/fixes together.
5. When count reaches `creator-alpha.max`, set `enforcePhaseCap=true` in `milestones.properties`.

## Reports

- HTML: `build/reports/spotbugs/main/index.html`
- CI artifact: `quality-reports` → `build/reports/spotbugs/`
