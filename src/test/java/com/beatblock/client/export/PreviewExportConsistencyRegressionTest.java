package com.beatblock.client.export;

import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.PlaybackStateDigest;
import com.beatblock.video.VideoExportSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Preview ↔ Export 一致性：同一冻结快照、同一时刻，Stage / Camera / VFX / Audio 对齐。
 */
class PreviewExportConsistencyRegressionTest {

	private static final int FPS = 60;
	private static final int SAMPLE_RATE = 44_100;

	@Test
	void exportFrameMatchesPresentationProbeAtMultipleTimes() {
		CompiledTimelineSnapshot program = VideoExportSyncFixtures.tenSecondShowcase();
		VideoExportSettings settings = exportSettings(0.0, 20.0);
		double[] probeTimes = {0.0, 2.0, 5.0, 8.0, 10.0, 19.9};

		for (double time : probeTimes) {
			int frameIndex = (int) Math.round(time * FPS);
			VideoExportFrameState exportFrame = VideoExportFrameSampler.sample(
				program,
				settings,
				frameIndex,
				VideoExportSyncFixtures.cameraAnchor(),
				0f,
				0f,
				SAMPLE_RATE
			);
			PresentationFrameProbe.Frame previewFrame = PresentationFrameProbe.atTime(
				program,
				time,
				VideoExportSyncFixtures.cameraAnchor(),
				0f,
				0f,
				SAMPLE_RATE
			);

			assertEquals(time, exportFrame.timelineTimeSeconds(), 1e-6, "time=" + time);
			assertEquals(previewFrame.timelineTimeSeconds(), exportFrame.timelineTimeSeconds(), 1e-9);
			assertEquals(previewFrame.audioSampleIndex(), exportFrame.audioSampleIndex());
			assertEquals(previewFrame.audioSourceTimeSeconds(), exportFrame.audioSourceTimeSeconds(), 1e-9);
			assertEquals(previewFrame.stageState(), exportFrame.stageState());
			assertEquals(previewFrame.vfxState(), exportFrame.vfxState());
			assertCameraEquals(previewFrame.camera(), exportFrame.camera());
		}
	}

	@Test
	void buildStageDigestReflectsCompletedBuildAtTenSeconds() {
		CompiledTimelineSnapshot program = VideoExportSyncFixtures.tenSecondShowcase();
		PlaybackStateDigest exportDigest = PlaybackStateDigest.reconstructAt(program, 10.0);
		assertTrue(exportDigest.stageStates().containsKey("stage-main"));
		assertEquals("BUILD:Pulse", exportDigest.stageStates().get("stage-main"));
		assertTrue(exportDigest.buildProgress().containsKey("stage-main"));
		assertEquals(1, exportDigest.buildProgress().get("stage-main").totalBlockCount());
		assertEquals(1, exportDigest.buildProgress().get("stage-main").completedBlockCount());
	}

	@Test
	void playToAndReconstructAgreeAcrossShowcaseDuration() {
		CompiledTimelineSnapshot program = VideoExportSyncFixtures.tenSecondShowcase();
		for (double time : new double[] {0.0, 5.0, 10.0, 15.0}) {
			assertEquals(
				PlaybackStateDigest.playTo(program, time),
				PlaybackStateDigest.reconstructAt(program, time),
				"play vs reconstruct at t=" + time
			);
		}
	}

	private static VideoExportSettings exportSettings(double start, double end) {
		return new VideoExportSettings(java.nio.file.Path.of("out/preview-export-sync.mp4"), 1920, 1080, FPS, start, end, true);
	}

	private static void assertCameraEquals(
		com.beatblock.client.camera.TimelineCameraEvaluator.CameraSample expected,
		com.beatblock.client.camera.TimelineCameraEvaluator.CameraSample actual
	) {
		assertNotNull(actual);
		assertEquals(expected.position().x, actual.position().x, 1e-6);
		assertEquals(expected.position().y, actual.position().y, 1e-6);
		assertEquals(expected.position().z, actual.position().z, 1e-6);
		assertEquals(expected.yawDeg(), actual.yawDeg(), 1e-4f);
		assertEquals(expected.pitchDeg(), actual.pitchDeg(), 1e-4f);
	}
}
