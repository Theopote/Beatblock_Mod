package com.beatblock.client.export;

import com.beatblock.client.camera.TimelineCameraEvaluator;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.PlaybackStateDigest;
import com.beatblock.video.VideoExportSettings;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 作品级视频导出同步回归：
 * Timeline 10.000s → frame 600 @60fps → Camera / Stage / VFX / Audio 严格对齐。
 */
class VideoExportSyncRegressionTest {

	private static final int FPS = 60;
	private static final int FRAME_INDEX = 600;
	private static final double TIMELINE_TIME = 10.0;
	private static final int SAMPLE_RATE = 44_100;

	@Test
	void frame600At60FpsAlignsTimelineAudioCameraStageAndVfx() {
		CompiledTimelineSnapshot program = VideoExportSyncFixtures.tenSecondShowcase();
		VideoExportSettings settings = exportSettings(0.0, 20.0);

		VideoExportFrameState frame = VideoExportFrameSampler.sample(
			program,
			settings,
			FRAME_INDEX,
			VideoExportSyncFixtures.cameraAnchor(),
			0f,
			0f,
			SAMPLE_RATE
		);

		assertEquals(FRAME_INDEX, frame.frameIndex());
		assertEquals(TIMELINE_TIME, frame.timelineTimeSeconds(), 1e-9);
		assertEquals(441_000L, frame.audioSampleIndex());
		assertEquals(TIMELINE_TIME, frame.audioSourceTimeSeconds(), 1e-9);

		PlaybackStateDigest playDigest = PlaybackStateDigest.playTo(program, TIMELINE_TIME);
		PlaybackStateDigest seekDigest = PlaybackStateDigest.reconstructAt(program, TIMELINE_TIME);
		assertEquals(playDigest, seekDigest, "formal playback and reconstruct must agree at export probe time");
		assertEquals(seekDigest, frame.stageState(), "export stage state must match compiled playback");

		TimelineCameraEvaluator.CameraSample expectedCamera = TimelineCameraEvaluator.evaluate(
			program.cameraTrack(),
			program.bpm(),
			TIMELINE_TIME,
			VideoExportSyncFixtures.cameraAnchor(),
			0f,
			0f
		);
		assertNotNull(expectedCamera);
		assertCameraEquals(expectedCamera, frame.camera());

		ExportVfxState expectedVfx = ExportVfxState.resolve(program.globalEvents(), TIMELINE_TIME);
		assertEquals(expectedVfx, frame.vfxState());
		assertEquals("tint=Chorus Tint;flash=Pre-Drop Flash", frame.vfxState().fingerprint());
	}

	@Test
	void frameSamplerMatchesCompositorTimelineTime() {
		CompiledTimelineSnapshot program = VideoExportSyncFixtures.tenSecondShowcase();
		VideoExportSettings settings = exportSettings(0.0, 20.0);
		VideoExportFrameState frame = VideoExportFrameSampler.sample(program, settings, FRAME_INDEX);

		byte[] rgba = opaqueFrame(4, 4);
		byte[] composited = GlobalVisualEffectFrameCompositor.composite(
			rgba.clone(),
			4,
			4,
			program.globalEvents(),
			frame.timelineTimeSeconds()
		);
		byte[] expected = GlobalVisualEffectFrameCompositor.composite(
			rgba.clone(),
			4,
			4,
			program.globalEvents(),
			TIMELINE_TIME
		);
		assertEquals(bytesToHex(expected), bytesToHex(composited));
	}

	private static VideoExportSettings exportSettings(double start, double end) {
		return new VideoExportSettings(tempPath(), 1920, 1080, FPS, start, end, true);
	}

	private static Path tempPath() {
		return Path.of("out/video-export-sync.mp4");
	}

	private static void assertCameraEquals(
		TimelineCameraEvaluator.CameraSample expected,
		TimelineCameraEvaluator.CameraSample actual
	) {
		assertNotNull(actual);
		assertEquals(expected.position().x, actual.position().x, 1e-6);
		assertEquals(expected.position().y, actual.position().y, 1e-6);
		assertEquals(expected.position().z, actual.position().z, 1e-6);
		assertEquals(expected.yawDeg(), actual.yawDeg(), 1e-4f);
		assertEquals(expected.pitchDeg(), actual.pitchDeg(), 1e-4f);
	}

	private static byte[] opaqueFrame(int width, int height) {
		byte[] frame = new byte[width * height * 4];
		for (int i = 3; i < frame.length; i += 4) {
			frame[i] = (byte) 255;
		}
		return frame;
	}

	private static String bytesToHex(byte[] bytes) {
		StringBuilder out = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			out.append(String.format("%02x", value));
		}
		return out.toString();
	}
}
