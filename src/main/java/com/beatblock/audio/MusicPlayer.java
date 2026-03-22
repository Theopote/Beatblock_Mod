package com.beatblock.audio;

import com.beatblock.timeline.IAudioPlayer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 音乐播放与进度控制，与 BeatScheduler 同步驱动时间轴。
 */
public class MusicPlayer implements IAudioPlayer {
	private static final Logger LOGGER = LoggerFactory.getLogger(MusicPlayer.class);

	private boolean playing;
	private double currentTimeSeconds;
	private double durationSeconds;
	private double playbackSpeed = 1.0;
	private Clip audioClip;
	private String loadedAudioPath;
	private String lastLoadError;

	public MusicPlayer() {
		this.playing = false;
		this.currentTimeSeconds = 0;
		this.durationSeconds = 0;
	}

	public boolean isPlaying() {
		syncClipState();
		return playing;
	}

	public void setPlaying(boolean playing) {
		this.playing = playing;
	}

	public void play() {
		syncClipState();
		if (audioClip != null) {
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				currentTimeSeconds = 0;
				audioClip.setMicrosecondPosition(0);
			}
			audioClip.start();
			LOGGER.info("BeatBlock MusicPlayer: playback started path={} time={}s", loadedAudioPath, String.format("%.3f", currentTimeSeconds));
		} else {
			LOGGER.warn("BeatBlock MusicPlayer: play requested but no audio clip is loaded. lastLoadError={}", lastLoadError);
		}
		playing = true;
	}

	public void pause() {
		if (audioClip != null) {
			audioClip.stop();
			currentTimeSeconds = clipPositionSeconds();
		}
		playing = false;
	}

	public void stop() {
		if (audioClip != null) {
			audioClip.stop();
			audioClip.setMicrosecondPosition(0);
		}
		playing = false;
		currentTimeSeconds = 0;
	}

	public double getCurrentTimeSeconds() {
		syncClipState();
		return currentTimeSeconds;
	}

	public void setCurrentTimeSeconds(double currentTimeSeconds) {
		if (durationSeconds > 0) {
			this.currentTimeSeconds = Math.max(0, Math.min(currentTimeSeconds, durationSeconds));
		} else {
			this.currentTimeSeconds = Math.max(0, currentTimeSeconds);
		}

		if (audioClip != null) {
			boolean restart = playing && audioClip.isRunning();
			audioClip.stop();
			audioClip.setMicrosecondPosition((long) Math.max(0, this.currentTimeSeconds * 1_000_000.0));
			if (restart) {
				audioClip.start();
			}
		}
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = Math.max(0, durationSeconds);
	}

	public double getPlaybackSpeed() {
		return playbackSpeed;
	}

	public void setPlaybackSpeed(double playbackSpeed) {
		this.playbackSpeed = Math.max(0.25, Math.min(4.0, playbackSpeed));
	}

	public boolean loadAudio(String path) {
		closeAudioClip();
		loadedAudioPath = null;
		lastLoadError = null;
		if (path == null || path.isBlank()) {
			lastLoadError = "未提供音频路径";
			return false;
		}

		Path file = Path.of(path).toAbsolutePath().normalize();
		if (!Files.isRegularFile(file)) {
			lastLoadError = "音频文件不存在: " + file;
			return false;
		}

		try {
			loadClip(file);
			loadedAudioPath = file.toString();
			playing = false;
			currentTimeSeconds = 0;
			lastLoadError = null;
			LOGGER.info("BeatBlock MusicPlayer: audio loaded path={} duration={}s", loadedAudioPath, String.format("%.3f", durationSeconds));
			return true;
		} catch (UnsupportedAudioFileException e) {
			LOGGER.warn("BeatBlock: unsupported audio format for {}: {}, trying ffmpeg fallback", file, e.getMessage());
			if (tryLoadViaFfmpegFallback(file)) {
				loadedAudioPath = file.toString();
				playing = false;
				currentTimeSeconds = 0;
				lastLoadError = null;
				LOGGER.info("BeatBlock MusicPlayer: audio loaded via ffmpeg PCM fallback path={} duration={}s", loadedAudioPath, String.format("%.3f", durationSeconds));
				return true;
			}
			if (lastLoadError == null || lastLoadError.isBlank()) {
				lastLoadError = "格式不受当前音频后端支持: " + file.getFileName();
			}
			closeAudioClip();
			return false;
		} catch (LineUnavailableException e) {
			lastLoadError = "无法打开系统音频输出设备";
			LOGGER.warn("BeatBlock: audio output unavailable for {}: {}", file, e.getMessage());
			closeAudioClip();
			return false;
		} catch (Exception e) {
			lastLoadError = "音频绑定失败: " + e.getMessage();
			LOGGER.warn("BeatBlock: audio playback unavailable for {}: {}", file, e.getMessage());
			closeAudioClip();
			return false;
		} catch (Throwable t) {
			lastLoadError = "音频后端运行时错误: " + t.getClass().getSimpleName();
			LOGGER.error("BeatBlock: runtime audio backend error for {}", file, t);
			closeAudioClip();
			return false;
		}
	}

	public String getLoadedAudioPath() {
		return loadedAudioPath;
	}

	public String getPlaybackStatusText() {
		if (loadedAudioPath == null || loadedAudioPath.isBlank()) {
			return "音频输出: 未绑定";
		}
		Path p = Path.of(loadedAudioPath);
		String name = p.getFileName() != null ? p.getFileName().toString() : loadedAudioPath;
		return "音频输出: " + name;
	}

	public String getLastLoadError() {
		return lastLoadError;
	}

	private void loadClip(Path file) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
		try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
			AudioFormat srcFmt = source.getFormat();
			AudioFormat pcmFmt = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				srcFmt.getSampleRate(),
				16,
				srcFmt.getChannels(),
				srcFmt.getChannels() * 2,
				srcFmt.getSampleRate(),
				false
			);
			try (AudioInputStream pcmStream = AudioSystem.getAudioInputStream(pcmFmt, source)) {
				Clip clip = acquireClip();
				clip.open(pcmStream);
				audioClip = clip;
				durationSeconds = clip.getMicrosecondLength() / 1_000_000.0;
			}
		}
	}

	private void syncClipState() {
		if (audioClip == null) return;
		currentTimeSeconds = clipPositionSeconds();
		if (playing && !audioClip.isRunning()) {
			if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds - 0.001) {
				currentTimeSeconds = durationSeconds;
			}
			playing = false;
		}
	}

	private double clipPositionSeconds() {
		if (audioClip == null) return currentTimeSeconds;
		return audioClip.getMicrosecondPosition() / 1_000_000.0;
	}

	private void closeAudioClip() {
		if (audioClip == null) return;
		try {
			audioClip.stop();
			audioClip.close();
		} finally {
			audioClip = null;
		}
	}

	private boolean tryLoadViaFfmpegFallback(Path originalFile) {
		String ffmpeg = resolveFfmpegExecutable();
		if (ffmpeg == null) {
			lastLoadError = "格式不受支持，且找不到 ffmpeg 进行解码兜底";
			return false;
		}

		// 直接输出裸 PCM（s16le）到 stdout，绕过 JavaSound 对 WAV 容器/头部兼容问题。
		final int sampleRate = 44_100;
		final int channels = 2;
		List<String> command = List.of(
			ffmpeg,
			"-y",
			"-i",
			originalFile.toAbsolutePath().toString(),
			"-vn",
			"-ar",
			String.valueOf(sampleRate),
			"-ac",
			String.valueOf(channels),
			"-acodec",
			"pcm_s16le",
			"-f",
			"s16le",
			"pipe:1"
		);

		StringBuilder output = new StringBuilder();
		int exitCode;
		byte[] pcmBytes;
		try {
			Process process = new ProcessBuilder(command)
				.redirectErrorStream(true)
				.start();
			ByteArrayOutputStream pcmOut = new ByteArrayOutputStream(1 << 20);
			try (var in = process.getInputStream()) {
				byte[] buffer = new byte[8192];
				int read;
				while ((read = in.read(buffer)) != -1) {
					pcmOut.write(buffer, 0, read);
					if (pcmOut.size() > 256 * 1024 * 1024) {
						process.destroyForcibly();
						lastLoadError = "音频过长，解码后内存占用过大";
						return false;
					}
				}
			}
			pcmBytes = pcmOut.toByteArray();
			exitCode = process.waitFor();
		} catch (Exception e) {
			lastLoadError = "ffmpeg 解码失败: " + e.getMessage();
			return false;
		}

		if (exitCode != 0 || pcmBytes == null || pcmBytes.length == 0) {
			lastLoadError = "ffmpeg 解码失败，退出码=" + exitCode;
			return false;
		}

		try {
			AudioFormat pcmFmt = new AudioFormat(
				AudioFormat.Encoding.PCM_SIGNED,
				sampleRate,
				16,
				channels,
				channels * 2,
				sampleRate,
				false
			);
			long frameLength = pcmBytes.length / pcmFmt.getFrameSize();
			try (AudioInputStream pcmStream = new AudioInputStream(new ByteArrayInputStream(pcmBytes), pcmFmt, frameLength)) {
				Clip clip = acquireClip();
				clip.open(pcmStream);
				audioClip = clip;
				durationSeconds = clip.getMicrosecondLength() / 1_000_000.0;
			}
			return true;
		} catch (Throwable e) {
			lastLoadError = "ffmpeg 已解码，但加载 PCM 失败: " + e.getMessage();
			return false;
		}
	}

	private Clip acquireClip() throws LineUnavailableException {
		try {
			return AudioSystem.getClip();
		} catch (NoSuchMethodError | SecurityException | IllegalArgumentException ignored) {
			DataLine.Info info = new DataLine.Info(Clip.class, null);
			Line line = AudioSystem.getLine(info);
			if (line instanceof Clip clip) {
				return clip;
			}
			throw new LineUnavailableException("系统未提供 Clip 音频线路");
		}
	}

	private String resolveFfmpegExecutable() {
		Path configPath = FabricLoader.getInstance().getConfigDir().resolve("beatblock/ffmpeg_path.txt");
		if (Files.exists(configPath)) {
			try {
				String txt = Files.readString(configPath).trim();
				if (!txt.isEmpty() && isExecutable(txt)) return txt;
			} catch (IOException ignored) {
			}
		}

		Path gameDir = FabricLoader.getInstance().getGameDir();
		List<Path> candidates = List.of(
			gameDir.resolve("ffmpeg.exe"),
			gameDir.resolve("ffmpeg"),
			gameDir.resolve("ffmpeg/bin/ffmpeg.exe"),
			gameDir.resolve("ffmpeg/bin/ffmpeg")
		);
		for (Path p : candidates) {
			if (Files.isRegularFile(p) && isExecutable(p.toAbsolutePath().toString())) {
				return p.toAbsolutePath().toString();
			}
		}

		if (isExecutable("ffmpeg")) return "ffmpeg";
		return null;
	}

	private boolean isExecutable(String executable) {
		try {
			Process p = new ProcessBuilder(executable, "-version")
				.redirectErrorStream(true)
				.start();
			boolean finished = p.waitFor(3, TimeUnit.SECONDS);
			return finished && p.exitValue() == 0;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * 每帧调用，推进播放进度（仅当 playing 时）。
	 */
	public void tick(double deltaSeconds) {
		if (audioClip != null) {
			syncClipState();
			return;
		}
		if (!playing) return;
		currentTimeSeconds = Math.max(0, currentTimeSeconds + deltaSeconds * playbackSpeed);
		if (durationSeconds > 0 && currentTimeSeconds >= durationSeconds) {
			currentTimeSeconds = durationSeconds;
			playing = false;
		}
	}
}
