package com.beatblock.ui.presenter;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.preferences.BeatBlockShortcutId;
import com.beatblock.ui.preferences.ShortcutValidation;
import com.beatblock.ui.preferences.UiPreferences;
import com.beatblock.ui.preferences.UiTheme;

import java.util.EnumMap;
import java.util.Map;

/**
 * Preferences Creator Surface: theme / shortcuts validation + persistence.
 * Panel must not call {@link UiPreferences} mutators directly.
 */
public final class PreferencesPresenter {

	public UiTheme theme() {
		return UiPreferences.theme();
	}

	public Map<BeatBlockShortcutId, String> allShortcuts() {
		return UiPreferences.allShortcuts();
	}

	/**
	 * Preview theme immediately; persist atomically.
	 * On disk failure still reports applied-for-session warning.
	 */
	public PresenterResult applyTheme(UiTheme theme) {
		UiTheme value = theme != null ? theme : UiTheme.DARK;
		boolean saved = UiPreferences.setTheme(value);
		if (saved) {
			return PresenterResult.success(BBTexts.get("beatblock.preferences.theme.applied"));
		}
		return PresenterResult.failure(BBTexts.get("beatblock.preferences.save_failed_session_only"));
	}

	public PresenterResult saveShortcuts(Map<BeatBlockShortcutId, String> drafts) {
		EnumMap<BeatBlockShortcutId, String> effective = new EnumMap<>(BeatBlockShortcutId.class);
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			String raw = drafts != null && drafts.get(id) != null ? drafts.get(id) : id.defaultChord();
			effective.put(id, raw);
		}
		ShortcutValidation.Result validation = ShortcutValidation.validate(effective);
		if (!validation.ok()) {
			return PresenterResult.failure(validation.firstErrorOrEmpty());
		}
		Map<BeatBlockShortcutId, String> normalized = ShortcutValidation.normalizeDraft(effective);
		boolean saved = UiPreferences.replaceShortcuts(normalized);
		if (!saved) {
			return PresenterResult.failure(BBTexts.get("beatblock.preferences.save_failed"));
		}
		return PresenterResult.success(BBTexts.get("beatblock.preferences.shortcuts.saved"));
	}

	public PresenterResult resetShortcuts() {
		boolean saved = UiPreferences.resetShortcuts();
		if (!saved) {
			return PresenterResult.failure(BBTexts.get("beatblock.preferences.save_failed"));
		}
		return PresenterResult.success(BBTexts.get("beatblock.preferences.shortcuts.reset_done"));
	}

	/** Reset theme + shortcuts to defaults (not acknowledgements / not project data). */
	public PresenterResult resetAppearanceAndShortcuts() {
		boolean saved = UiPreferences.resetAppearanceAndShortcuts();
		if (!saved) {
			return PresenterResult.failure(BBTexts.get("beatblock.preferences.save_failed"));
		}
		return PresenterResult.success(BBTexts.get("beatblock.preferences.reset_defaults_done"));
	}
}
