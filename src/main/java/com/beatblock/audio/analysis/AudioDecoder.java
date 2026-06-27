package com.beatblock.audio.analysis;

import com.beatblock.audio.DecodedAudio;
import com.beatblock.audio.WavDecoder;

import org.jspecify.annotations.Nullable;

import java.nio.file.Path;

/**
 * 解码音频为 PCM（AudioBuffer）。当前支持 WAV；可扩展 MP3/OGG（如 JLayer、Tritonus）。
 */
public final class AudioDecoder {

	/**
	 * 从文件路径加载并解码，返回 AudioBuffer；失败返回 null。
	 */
	public static @Nullable AudioBuffer load(@Nullable Path path) {
		if (path == null) return null;
		DecodedAudio decoded = WavDecoder.loadFromPath(path.toString());
		return fromDecodedAudio(decoded);
	}

	/**
	 * 从已有 DecodedAudio 转为 AudioBuffer（单声道）。
	 */
	public static @Nullable AudioBuffer fromDecodedAudio(@Nullable DecodedAudio decoded) {
		if (decoded == null) return null;
		return new AudioBuffer(decoded.getSamples(), decoded.getSampleRate(), 1);
	}
}
