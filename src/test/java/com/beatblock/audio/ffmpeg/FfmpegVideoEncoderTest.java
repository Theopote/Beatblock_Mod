package com.beatblock.audio.ffmpeg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FfmpegVideoEncoderTest {

	@Test
	void buildVideoCommandWithoutAudio() {
		List<String> cmd = FfmpegVideoEncoder.buildVideoCommand(
			"C:/ffmpeg/ffmpeg.exe",
			Path.of("out/export.mp4"),
			1920,
			1080,
			30,
			null,
			0.0
		);
		assertEquals("C:/ffmpeg/ffmpeg.exe", cmd.get(0));
		assertTrue(cmd.contains("-f"));
		assertTrue(cmd.contains("rawvideo"));
		assertTrue(cmd.contains("1920x1080"));
		assertTrue(cmd.contains("pipe:0"));
		assertTrue(cmd.contains("libx264"));
		assertTrue(cmd.stream().noneMatch("aac"::equals));
	}

	@Test
	void buildVideoCommandWithAudio(@TempDir Path tempDir) throws Exception {
		Path audio = tempDir.resolve("track.mp3");
		Files.writeString(audio, "fake");
		List<String> cmd = FfmpegVideoEncoder.buildVideoCommand(
			"ffmpeg",
			tempDir.resolve("out.mp4"),
			1280,
			720,
			24,
			audio,
			0.0
		);
		assertTrue(cmd.contains(audio.toAbsolutePath().toString()));
		assertTrue(cmd.contains("aac"));
		assertTrue(cmd.contains("-shortest"));
		assertTrue(cmd.stream().noneMatch("-ss"::equals));
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
			12.5
		);
		int ssIndex = cmd.indexOf("-ss");
		int audioIndex = cmd.indexOf(audio.toAbsolutePath().toString());
		assertTrue(ssIndex >= 0);
		assertTrue(ssIndex < audioIndex);
		assertEquals("12.5", cmd.get(ssIndex + 1));
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
