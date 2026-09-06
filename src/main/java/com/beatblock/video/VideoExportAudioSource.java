package com.beatblock.video;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import org.jspecify.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the project audio file used when {@code includeAudio} is true.
 * Missing / invalid sources must block export — never silent video-only fallback.
 */
public final class VideoExportAudioSource {

	private VideoExportAudioSource() {
	}

	public static boolean isAvailable(@Nullable BeatBlockContext context) {
		return resolve(context) != null;
	}

	public static @Nullable Path resolve(@Nullable BeatBlockContext context) {
		if (context == null) {
			return null;
		}
		MusicPlayer musicPlayer = context.musicPlayer();
		if (musicPlayer != null) {
			String loaded = musicPlayer.getLoadedAudioPath();
			Path fromPlayer = existingFile(loaded);
			if (fromPlayer != null) {
				return fromPlayer;
			}
		}
		Timeline timeline = context.timeline();
		if (timeline != null) {
			Object audioPath = timeline.getMetadata("audioPath");
			if (audioPath != null) {
				return existingFile(String.valueOf(audioPath));
			}
		}
		return null;
	}

	public static @Nullable String displayPath(@Nullable BeatBlockContext context) {
		Path path = resolve(context);
		return path != null ? path.toString() : null;
	}

	private static @Nullable Path existingFile(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			Path path = Path.of(raw);
			return Files.isRegularFile(path) ? path : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}
}
