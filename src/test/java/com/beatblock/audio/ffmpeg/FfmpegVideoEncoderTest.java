package com.beatblock.audio.ffmpeg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegVideoEncoderTest {

	private static final int SAMPLE_RATE = 44_100;

	@TempDir
	Path tempDir;

	@Test
	void frameClockAlignsWithFfmpegAudioSeekBase() throws Exception {
		Path audio = tempDir.resolve("track.wav");
		Files.writeString(audio, "fake");
		com.beatblock.video.VideoExportSettings settings = new com.beatblock.video.VideoExportSettings(
			tempDir.resolve("out.mp4"), 1920, 1080, 60, 5.0, 25.0, true);

		List<String> cmd = FfmpegVideoEncoder.buildVideoCommand(
			"ffmpeg",
			settings.outputPath(),
			1920,
			1080,
			60,
			audio,
			settings.startTimeSeconds(),
			settings.encodedDurationSeconds()
		);
		int ssIndex = cmd.indexOf("-ss");
		assertTrue(ssIndex >= 0);
		assertEquals("5", cmd.get(ssIndex + 1));
		int tIndex = cmd.indexOf("-t");
		assertTrue(tIndex >= 0);
		assertEquals("20", cmd.get(tIndex + 1));

		long sampleAtProbe = com.beatblock.client.export.VideoExportFrameClock.audioSampleIndex(settings, 300, SAMPLE_RATE);
		assertEquals(441_000L, sampleAtProbe);
		assertEquals(10.0,
			com.beatblock.client.export.VideoExportFrameClock.audioTimeFromSampleIndex(sampleAtProbe, SAMPLE_RATE),
			1e-9);
	}

	@Test
	void buildVideoCommandWithoutAudio() {
		List<String> cmd = FfmpegVideoEncoder.buildVideoCommand(
			"C:/ffmpeg/ffmpeg.exe",
			Path.of("out/export.mp4"),
			1920,
			1080,
			30,
			null,
			0.0,
			10.0
		);
		assertEquals("C:/ffmpeg/ffmpeg.exe", cmd.get(0));
		assertTrue(cmd.contains("-f"));
		assertTrue(cmd.contains("rawvideo"));
		assertTrue(cmd.contains("1920x1080"));
		assertTrue(cmd.contains("pipe:0"));
		assertTrue(cmd.contains("libx264"));
		assertTrue(cmd.stream().noneMatch("aac"::equals));
		assertTrue(cmd.stream().noneMatch("-t"::equals));
	}

	@Test
	void buildVideoCommandWithAudioUsesExplicitDuration(@TempDir Path tempDir) throws Exception {
		Path audio = tempDir.resolve("track.mp3");
		Files.writeString(audio, "fake");
		List<String> cmd = FfmpegVideoEncoder.buildVideoCommand(
			"ffmpeg",
			tempDir.resolve("out.mp4"),
			1280,
			720,
			24,
			audio,
			0.0,
			10.0
		);
		assertTrue(cmd.contains(audio.toAbsolutePath().toString()));
		assertTrue(cmd.contains("aac"));
		assertTrue(cmd.contains("-shortest"));
		assertTrue(cmd.stream().noneMatch("-ss"::equals));
		int tIndex = cmd.indexOf("-t");
		assertTrue(tIndex >= 0);
		assertEquals("10", cmd.get(tIndex + 1));
		assertTrue(tIndex < cmd.indexOf(audio.toAbsolutePath().toString()));
	}

	@Test
	void buildVideoCommandWithAudioSeek(@TempDir Path tempDir) throws Exception {
		Path audio = tempDir.resolve("track.mp3");
		Files.writeString(audio, "fake");
		List<String> cmd = FfmpegVideoEncoder.buildVideoCommand(
			"ffmpeg",
			tempDir.resolve("out.mp4"),
			1920,
			1080,
			60,
			audio,
			12.5,
			7.5
		);
		int ssIndex = cmd.indexOf("-ss");
		int tIndex = cmd.indexOf("-t");
		int audioIndex = cmd.indexOf(audio.toAbsolutePath().toString());
		assertTrue(ssIndex >= 0);
		assertTrue(tIndex >= 0);
		assertTrue(ssIndex < audioIndex);
		assertTrue(tIndex < audioIndex);
		assertEquals("12.5", cmd.get(ssIndex + 1));
		assertEquals("7.5", cmd.get(tIndex + 1));
	}

	@Test
	void formatFfmpegSecondsTrimsTrailingZeros() {
		assertEquals("30", FfmpegVideoEncoder.formatFfmpegSeconds(30.0));
		assertEquals("12.5", FfmpegVideoEncoder.formatFfmpegSeconds(12.5));
		assertEquals("0", FfmpegVideoEncoder.formatFfmpegSeconds(0.0));
	}

	@Test
	void defaultOutputFileNameUsesStem() {
		String name = FfmpegVideoEncoder.defaultOutputFileName("C:/music/My Song.wav");
		assertTrue(name.startsWith("My Song_"));
		assertTrue(name.endsWith(".mp4"));
	}
}
