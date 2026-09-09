package com.beatblock.ui.preferences;

import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates shortcut drafts against {@link ShortcutChord} semantics + conflict detection. */
public final class ShortcutValidation {

	private ShortcutValidation() {}

	public record Result(boolean ok, List<String> errors) {
		public Result {
			errors = List.copyOf(errors != null ? errors : List.of());
		}

		public static Result success() {
			return new Result(true, List.of());
		}

		public static Result failure(List<String> errors) {
			return new Result(false, errors);
		}

		public String firstErrorOrEmpty() {
			return errors.isEmpty() ? "" : errors.getFirst();
		}
	}

	/**
	 * Validate a full shortcut draft map (effective chords for every action).
	 * Rejects unsupported chords and duplicate normalized chords.
	 */
	public static Result validate(@Nullable Map<BeatBlockShortcutId, String> drafts) {
		if (drafts == null || drafts.isEmpty()) {
			return Result.failure(List.of(BBTexts.get("beatblock.preferences.shortcuts.empty_draft")));
		}
		List<String> errors = new ArrayList<>();
		Map<String, BeatBlockShortcutId> owners = new LinkedHashMap<>();
		EnumMap<BeatBlockShortcutId, ShortcutChord> parsed = new EnumMap<>(BeatBlockShortcutId.class);

		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			String raw = drafts.get(id);
			if (raw == null) {
				raw = id.defaultChord();
			}
			ShortcutChord chord = ShortcutChord.parse(raw);
			if (chord == null) {
				errors.add(BBTexts.get(
					"beatblock.preferences.shortcuts.invalid",
					label(id),
					raw != null ? raw.trim() : ""));
				continue;
			}
			parsed.put(id, chord);
			String normalized = chord.normalize();
			BeatBlockShortcutId existing = owners.putIfAbsent(normalized, id);
			if (existing != null) {
				errors.add(BBTexts.get(
					"beatblock.preferences.shortcuts.conflict",
					normalized,
					label(existing),
					label(id)));
			}
		}
		return errors.isEmpty() ? Result.success() : Result.failure(errors);
	}

	/** Normalized chord strings suitable for persistence (only custom/non-default needed by store). */
	public static EnumMap<BeatBlockShortcutId, String> normalizeDraft(Map<BeatBlockShortcutId, String> drafts) {
		EnumMap<BeatBlockShortcutId, String> out = new EnumMap<>(BeatBlockShortcutId.class);
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			String raw = drafts != null && drafts.get(id) != null ? drafts.get(id) : id.defaultChord();
			ShortcutChord chord = ShortcutChord.parse(raw);
			if (chord == null) {
				continue;
			}
			String normalized = chord.normalize();
			if (!normalized.equalsIgnoreCase(id.defaultChord())) {
				out.put(id, normalized);
			}
		}
		return out;
	}

	private static String label(BeatBlockShortcutId id) {
		String key = "beatblock.preferences.shortcut." + id.id();
		String translated = BBTexts.get(key);
		if (translated == null || translated.isBlank() || translated.equals(key)) {
			return id.id();
		}
		return translated;
	}
}
