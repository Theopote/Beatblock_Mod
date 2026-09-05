package com.beatblock.timeline.project;

import com.beatblock.timeline.Timeline;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Persists Timeline metadata keys that belong to audio clips / project audio binding.
 * <p>
 * Stored under {@code clipMetadata} in .osc so Properties edits to label/timing survive Save/Reload
 * together with the audio track clips in {@link TimelineAnimationPersistence}.
 */
public final class TimelineClipMetadataPersistence {

	private static final List<String> EXACT_KEYS = List.of(
		"audioRootClipId",
		"audioAssetId"
	);

	private static final List<String> PREFIXES = List.of(
		"clipLabel_",
		"clipAudioPath_",
		"clipAudioKey_"
	);

	private TimelineClipMetadataPersistence() {}

	public static @Nullable JsonObject toJson(@Nullable Timeline timeline) {
		if (timeline == null) {
			return null;
		}
		JsonObject out = new JsonObject();
		for (Map.Entry<String, Object> entry : timeline.getMetadata().entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			if (key == null || value == null || !isPersistedKey(key)) {
				continue;
			}
			String text = String.valueOf(value);
			if (!text.isBlank()) {
				out.addProperty(key, text);
			}
		}
		return out.size() == 0 ? null : out;
	}

	public static void loadInto(@Nullable Timeline timeline, @Nullable JsonElement element) {
		if (timeline == null) {
			return;
		}
		clearPersistedKeys(timeline);
		if (element == null || element.isJsonNull() || !element.isJsonObject()) {
			return;
		}
		JsonObject obj = element.getAsJsonObject();
		for (String key : obj.keySet()) {
			if (!isPersistedKey(key)) {
				continue;
			}
			JsonElement value = obj.get(key);
			if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
				continue;
			}
			String text = value.getAsString();
			timeline.setMetadata(key, text == null || text.isBlank() ? null : text);
		}
	}

	static boolean isPersistedKey(String key) {
		if (key == null || key.isBlank()) {
			return false;
		}
		for (String exact : EXACT_KEYS) {
			if (exact.equals(key)) {
				return true;
			}
		}
		for (String prefix : PREFIXES) {
			if (key.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static void clearPersistedKeys(Timeline timeline) {
		List<String> toClear = new ArrayList<>();
		for (String key : timeline.getMetadata().keySet()) {
			if (isPersistedKey(key)) {
				toClear.add(key);
			}
		}
		for (String key : toClear) {
			timeline.setMetadata(key, null);
		}
	}
}
