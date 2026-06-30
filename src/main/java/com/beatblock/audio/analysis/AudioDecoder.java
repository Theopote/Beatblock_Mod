package com.beatblock.audio.analysis;

import com.beatblock.audio.DecodedAudio;
import com.beatblock.audio.WavDecoder;
import com.beatblock.audio.ffmpeg.FfmpegService;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 解码音频为 PCM（AudioBuffer）。优先 JavaSound/WAV；不支持时回退 ffmpeg（与 {@link com.beatblock.audio.MusicPlayer} 一致）。
 */
public final class AudioDecoder {

	private static final Logger LOGGER = LoggerFactory.getLogger(AudioDecoder.class);
	private static final int MAX_PCM_BYTES = 256 * 1024 * 1024;
	private static final int[][] FFMPEG_CANDIDATES = {
		{44_100, 1}, {48_000, 1}, {44_100, 2}, {48_000, 2}
	};

	private AudioDecoder() {
	}

	/**
	 * 从文件路径加载并解码，返回 AudioBuffer；失败返回 null。
	 */
	public static @Nullable AudioBuffer load(@Nullable Path path) {
		if (path == null) return null;
		DecodedAudio decoded = WavDecoder.loadFromPath(path.toString());
		if (decoded == null) {
			decoded = loadViaFfmpegFallback(path);
		}
		return fromDecodedAudio(decoded);
	}

	/**
	 * 从已有 DecodedAudio 转为 AudioBuffer（单声道）。
	 */
	public static @Nullable AudioBuffer fromDecodedAudio(@Nullable DecodedAudio decoded) {
		if (decoded == null) return null;
		return new AudioBuffer(decoded.getSamples(), decoded.getSampleRate(), 1);
	}

	private static @Nullable DecodedAudio loadViaFfmpegFallback(Path path) {
		if (!Files.isRegularFile(path) || !FfmpegService.isAvailable()) {
			return null;
		}
		Exception lastFailure = null;
		for (int[] candidate : FFMPEG_CANDIDATES) {
			int sampleRate = candidate[0];
			int channels = candidate[1];
			try {
				byte[] pcm = FfmpegService.decodeToPcm(path, sampleRate, channels, MAX_PCM_BYTES);
				float[] mono = pcmS16LeToMonoFloat(pcm, channels);
				if (mono.length == 0) {
					continue;
				}
				double durationSeconds = mono.length / (double) sampleRate;
				LOGGER.info(
					"BeatBlock AudioDecoder: ffmpeg fallback selected {}Hz/{}ch path={}",
					sampleRate, channels, path.getFileName()
				);
				return new DecodedAudio(mono, sampleRate, durationSeconds);
			} catch (Exception e) {
				lastFailure = e;
				LOGGER.debug(
					"BeatBlock AudioDecoder: ffmpeg candidate {}Hz/{}ch rejected path={} reason={}",
					sampleRate, channels, path.getFileName(), e.getMessage()
				);
			}
		}
		if (lastFailure != null) {
			LOGGER.warn(
				"BeatBlock AudioDecoder: ffmpeg fallback failed path={} reason={}",
				path, lastFailure.getMessage()
			);
		}
		return null;
	}

	static float[] pcmS16LeToMonoFloat(byte[] pcm, int channels) {
		if (pcm == null || pcm.length == 0 || channels <= 0) {
			return new float[0];
		}
		int frameSize = channels * 2;
		int frames = pcm.length / frameSize;
		float[] mono = new float[frames];
		for (int i = 0; i < frames; i++) {
			int offset = i * frameSize;
			float sum = 0f;
			for (int c = 0; c < channels; c++) {
				int idx = offset + c * 2;
				int lo = pcm[idx] & 0xFF;
				int hi = pcm[idx + 1];
				short sample = (short) ((hi << 8) | lo);
				sum += sample / 32768f;
			}
			mono[i] = sum / channels;
		}
		return mono;
	}
}
