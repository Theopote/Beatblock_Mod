package com.beatblock.timeline.playback;

import com.beatblock.testutil.MinecraftTestBootstrap;
import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static com.beatblock.timeline.playback.GoldenPlaybackContractFixtures.BLOCK_COUNT;
import static com.beatblock.timeline.playback.GoldenPlaybackContractFixtures.BUILD_END;
import static com.beatblock.timeline.playback.GoldenPlaybackContractFixtures.LOOP_OUT;
import static com.beatblock.timeline.playback.GoldenPlaybackContractFixtures.PROBE;
import static com.beatblock.timeline.playback.GoldenPlaybackContractFixtures.STAGE_ID;
import static com.beatblock.timeline.playback.GoldenPlaybackContractFixtures.cameraAnchor;
import static com.beatblock.timeline.playback.GoldenPlaybackContractFixtures.exportProbe;
import static com.beatblock.timeline.playback.GoldenPlaybackContractFixtures.program;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Golden Playback Contract：A play == B seek == C loop wrap == D export。
 * <p>
 * Stage = 2048 blocks（跨 chunk）；BUILD 5s→15s；PLACE 18s；CLEAR 24s；probe = 12s。
 */
class GoldenPlaybackContractTest {

	@BeforeAll
	static void bootstrap() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void playSeekLoopExportAgreeAtProbe12s(@TempDir Path tempDir) {
		CompiledTimelineSnapshot snapshot = program();

		assertEquals(BLOCK_COUNT, countStageBlocks(snapshot));
		assertTrue(countDistinctChunks(snapshot) > 1, "fixture must span multiple chunks");

		WorldStateFingerprint a = WorldStateFingerprint.playTo(snapshot, PROBE, cameraAnchor());
		WorldStateFingerprint b = WorldStateFingerprint.seekTo(snapshot, PROBE, cameraAnchor());
		WorldStateFingerprint c = WorldStateFingerprint.loopWrapTo(
			snapshot, LOOP_OUT, PROBE, cameraAnchor());
		WorldStateFingerprint d = exportProbe(snapshot, tempDir.resolve("golden.mp4"));

		WorldStateFingerprint atLoopOut = WorldStateFingerprint.seekTo(snapshot, LOOP_OUT, cameraAnchor());
		assertNotEquals(a, atLoopOut, "loopOut state must differ from probe (contract not vacuous)");

		assertMidBuildProgress(a);
		assertEquals(a, b, () -> "A play != B seek\nA=" + a.summary() + "\nB=" + b.summary());
		assertEquals(b, c, () -> "B seek != C loop\nB=" + b.summary() + "\nC=" + c.summary());
		assertEquals(c, d, () -> "C loop != D export\nC=" + c.summary() + "\nD=" + d.summary());
		assertEquals(a, d, () -> "A play != D export\nA=" + a.summary() + "\nD=" + d.summary());
	}

	@Test
	void probeIsStrictlyMidBuildNotPlaceOrClear() {
		CompiledTimelineSnapshot snapshot = program();
		WorldStateFingerprint atProbe = WorldStateFingerprint.seekTo(snapshot, PROBE, cameraAnchor());
		WorldStateFingerprint afterBuild = WorldStateFingerprint.seekTo(snapshot, BUILD_END, cameraAnchor());
		WorldStateFingerprint afterPlace = WorldStateFingerprint.seekTo(snapshot, 19.0, cameraAnchor());

		BuildProgressDigest mid = atProbe.buildProgress().get(STAGE_ID);
		BuildProgressDigest done = afterBuild.buildProgress().get(STAGE_ID);
		assertTrue(mid != null && done != null);
		assertTrue(mid.completedBlockCount() > 0);
		assertTrue(mid.completedBlockCount() < BLOCK_COUNT);
		assertEquals(BLOCK_COUNT, done.completedBlockCount());
		assertTrue(atProbe.visibleBlockCount() < afterBuild.visibleBlockCount());

		assertTrue(atProbe.animatedStateFingerprint().contains("BUILD"));
		assertTrue(afterPlace.animatedStateFingerprint().contains("PLACE"));
	}

	private static void assertMidBuildProgress(WorldStateFingerprint fingerprint) {
		assertEquals(PROBE, fingerprint.timeSeconds(), 1e-9);
		BuildProgressDigest progress = fingerprint.buildProgress().get(STAGE_ID);
		assertTrue(progress != null, "BUILD progress missing at probe");
		assertEquals(BLOCK_COUNT, progress.totalBlockCount());
		// BUILD 5→15s 线性：t=12 → ceil(0.7 * 2048) = 1434
		assertEquals(1434, progress.completedBlockCount());
		assertTrue(
			progress.completedBlockCount() > 768,
			"probe should reveal more than one realtime budget tick: " + progress.completedBlockCount()
		);
		assertTrue(
			progress.completedBlockCount() < BLOCK_COUNT,
			"probe must be mid-BUILD, not complete"
		);
		assertEquals(progress.completedBlockCount(), fingerprint.visibleBlockCount());
		assertTrue(fingerprint.animatedStateFingerprint().contains("BUILD:" ));
		assertTrue(fingerprint.cameraFingerprint().length() > 0);
		assertTrue(fingerprint.vfxFingerprint().contains("tint="));
	}

	private static int countStageBlocks(CompiledTimelineSnapshot snapshot) {
		for (CompiledStageEvent compiled : snapshot.compiledStageEvents()) {
			if (compiled.target() != null && STAGE_ID.equals(compiled.target().id())) {
				return compiled.target().blocks().size();
			}
		}
		return 0;
	}

	private static int countDistinctChunks(CompiledTimelineSnapshot snapshot) {
		Set<Long> chunks = new HashSet<>();
		for (CompiledStageEvent compiled : snapshot.compiledStageEvents()) {
			if (compiled.target() == null) continue;
			for (var pos : compiled.target().blocks()) {
				if (pos == null) continue;
				ChunkPos chunk = new ChunkPos(pos);
				chunks.add(ChunkPos.toLong(chunk.x, chunk.z));
			}
		}
		return chunks.size();
	}
}
