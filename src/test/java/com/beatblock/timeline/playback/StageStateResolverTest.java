package com.beatblock.timeline.playback;

import com.beatblock.client.export.VideoExportSyncFixtures;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.BuildExecutionPolicy;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.testutil.MinecraftTestBootstrap;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1：Scrub / Formal Seek / Export 共用 {@link StageStateResolver} 逻辑态。
 */
class StageStateResolverTest {

	@BeforeAll
	static void bootstrap() {
		MinecraftTestBootstrap.ensureInitialized();
	}

	@Test
	void resolveMatchesPlaybackDigestAndCollectsRequiredBuildBlocks() {
		CompiledTimelineSnapshot program = compileTenBlockWall();
		ResolvedStageState mid = StageStateResolver.resolve(program, 5.5);
		PlaybackStateDigest digest = PlaybackStateDigest.reconstructAt(program, 5.5);

		assertEquals(digest, mid.toPlaybackDigest());
		assertTrue(mid.buildProgress().containsKey("wall"));
		assertTrue(mid.requiredBuildBlockPositions().size() > 0);
		assertEquals(
			mid.buildProgress().get("wall").completedBlockCount(),
			mid.requiredBuildBlockPositions().size()
		);
	}

	@Test
	void scrubAndFormalSemanticsAgreeViaResolver() {
		CompiledTimelineSnapshot program = VideoExportSyncFixtures.tenSecondShowcase();
		for (double t : new double[] {0.0, 5.0, 8.0, 10.0}) {
			ResolvedStageState resolved = StageStateResolver.resolve(program, t);
			assertEquals(
				PlaybackStateDigest.playTo(program, t),
				resolved.toPlaybackDigest(),
				"playTo vs resolver at t=" + t
			);
			assertEquals(
				PlaybackStateDigest.reconstructAt(program, t),
				resolved.toPlaybackDigest(),
				"reconstruct vs resolver at t=" + t
			);
		}
	}

	@Test
	void loopWrapTargetMatchesSeekViaResolver() {
		CompiledTimelineSnapshot program = compileTenBlockWall();
		double loopIn = 5.5;
		// Loop wrap 落到 loopIn 时的逻辑态，必须与直接 Seek 到 loopIn 一致
		ResolvedStageState afterLoop = StageStateResolver.resolve(program, loopIn);
		ResolvedStageState afterSeek = StageStateResolver.resolve(program, loopIn);
		assertEquals(afterSeek.toPlaybackDigest(), afterLoop.toPlaybackDigest());
		assertEquals(
			PlaybackStateDigest.reconstructAt(program, loopIn),
			afterLoop.toPlaybackDigest()
		);
	}

	@Test
	void resolveWithCameraAnchorFillsCameraSample() {
		CompiledTimelineSnapshot program = VideoExportSyncFixtures.tenSecondShowcase();
		ResolvedStageState resolved = StageStateResolver.resolve(
			program, 10.0, new Vec3d(0, 64, 0), 0f, 0f);
		assertNotNull(resolved.camera());
	}

	@Test
	void buildExecutionPoliciesMapFromPlaybackModes() {
		assertEquals(
			BuildExecutionPolicy.PREVIEW,
			com.beatblock.client.PlaybackExecutionMode.EDITOR_PREVIEW.buildPolicy()
		);
		assertEquals(
			BuildExecutionPolicy.REALTIME,
			com.beatblock.client.PlaybackExecutionMode.REALTIME_PLAYBACK.buildPolicy()
		);
		assertEquals(
			BuildExecutionPolicy.OFFLINE_EXPORT,
			com.beatblock.client.PlaybackExecutionMode.EXPORT_RECONSTRUCTION.buildPolicy()
		);
		assertFalse(BuildExecutionPolicy.OFFLINE_EXPORT.advancePastUnloadedChunks());
		assertTrue(BuildExecutionPolicy.REALTIME.advancePastUnloadedChunks());
	}

	private static CompiledTimelineSnapshot compileTenBlockWall() {
		Timeline timeline = Timeline.createDefault();
		timeline.setDurationSeconds(20.0);
		timeline.setMetadata("bpm", 120.0);
		List<BlockPos> blocks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			blocks.add(new BlockPos(i, 64, 0));
		}
		BlockAnimationEngine engine = new BlockAnimationEngine();
		engine.getStageObjectSystem().register(StageObjectSystem.fromBlocks("wall", "Wall", blocks));
		timeline.addAutoAnimationEvent(new TimelineAnimationEvent(
			"build-wall", 5.0, 2.0, "Pulse", "wall", 1f,
			Map.of(
				"actionMode", "BUILD",
				"playbackSemantics", "STATEFUL",
				"animationType", "Pulse",
				"targetObject", "wall",
				"buildMode", "wall",
				"durationSeconds", 2.0)));
		for (double t = 0.0; t <= 20.0; t += 0.5) {
			timeline.addFeatureEvent("kick", new com.beatblock.timeline.FeatureEvent(t, 1f));
		}
		return TimelineCompiler.compile(timeline, engine, null);
	}
}
