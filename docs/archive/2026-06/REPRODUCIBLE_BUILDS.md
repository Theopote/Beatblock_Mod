# Reproducible builds (P1-8)

Goal: **same git commit + same time-independent tooling ⇒ same dependency graph and auditable release jar**.

## What is pinned

| Layer | Pin | Location |
|-------|-----|----------|
| Gradle distribution | `9.3.0` + SHA-256 | `gradle/wrapper/gradle-wrapper.properties` (`distributionUrl`, `distributionSha256Sum`) |
| Fabric Loom | **1.15.5** (not `*-SNAPSHOT`) | `gradle.properties` → `loom_version` |
| MC / Yarn / Loader / FAPI | exact versions | `gradle.properties` |
| Transitive graph | lockfile | `gradle.lockfile` |
| Artifact checksums | verification metadata | `gradle/verification-metadata.xml` |
| Archive layout | stable timestamps / order | `build.gradle` → `AbstractArchiveTask` |

## Daily development

Use the wrapper only:

```bat
gradlew.bat test
gradlew.bat build
```

Do **not** install a floating global Gradle for CI/release.

## After intentional dependency upgrades

1. Edit the version in `gradle.properties` / `build.gradle`.
2. Refresh locks and checksums:

```bat
gradlew.bat dependencies --write-locks
gradlew.bat --write-verification-metadata sha256 help classes testClasses spotbugsMain
```

3. Review the diff of `gradle.lockfile` and `gradle/verification-metadata.xml`.
4. Commit together with the version change.

`org.gradle.dependency.verification=strict` is set in `gradle.properties`.  
If a local experiment needs to bypass verification temporarily:

```bat
gradlew.bat build -Dorg.gradle.dependency.verification=off
```

Do not leave verification off on the default branch.

## Release: tag ↔ unique jar

```bat
gradlew.bat clean remapJar fingerprintReleaseJar
```

Artifacts:

- `build/libs/beatblock-<version>.jar` — remapped mod jar
- `build/libs/beatblock-release.sha256` — `SHA-256` of that jar

Recommended release checklist:

1. Ensure working tree is clean and matches the release commit.
2. Run the command above.
3. Create git tag: `v<mod_version>` (must match `mod_version` in `gradle.properties`).
4. Attach **both** the jar and the `.sha256` file to the release notes / release assets.
5. Optional CI gate against a known hash:

```bat
gradlew.bat verifyReleaseFingerprint -PreleaseSha256=<hex>
```

Anyone can re-check:

```bat
certutil -hashfile build\libs\beatblock-1.0.0.jar SHA256
```

(or `sha256sum` on Unix). The hex must match `beatblock-release.sha256`.

## Why Loom was not left on SNAPSHOT

`loom_version=1.15-SNAPSHOT` means the same commit can resolve a different Loom binary on different days. That breaks reproducibility and can silently change remapping behavior. **1.15.5** is the latest stable patch on the 1.15 line used by this project.

## Limits (honest)

- Full bit-identical remapped jars also depend on Loom/Minecraft toolchains; we pin inputs and stabilize archive metadata.
- Mojang may re-host libraries; verification will fail loudly (good) until metadata is regenerated on purpose.
- Nested jar-in-jar contents are checked by `verifyBundledDependencies` (presence), not full nested digests.

## Related tasks

| Task | Purpose |
|------|---------|
| `verifyBundledDependencies` | nested META-INF/jars present in remap jar |
| `fingerprintReleaseJar` | write release SHA-256 |
| `verifyReleaseFingerprint` | compare against `-PreleaseSha256` |
| `check` | includes bundle + SpotBugs gates |
