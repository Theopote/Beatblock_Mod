package com.beatblock.ui.preferences;

import com.beatblock.ui.presenter.PreferencesPresenter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreferencesHardeningTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		System.setProperty("beatblock.test.configDir", tempDir.toString());
		UiPreferences.resetForTests();
	}

	@AfterEach
	void tearDown() {
		UiPreferences.resetForTests();
		System.clearProperty("beatblock.test.configDir");
	}

	@Test
	void shortcutChordRejectsUnsupportedKeys() {
		assertNull(ShortcutChord.parse("Ctrl+K"));
		assertNull(ShortcutChord.parse("F5"));
		assertNull(ShortcutChord.parse("Space"));
		assertNotNull(ShortcutChord.parse("Ctrl+S"));
		assertEquals("Ctrl+Shift+O", ShortcutChord.parse("ctrl+shift+o").normalize());
	}

	@Test
	void validationRejectsInvalidAndConflicts() {
		EnumMap<BeatBlockShortcutId, String> drafts = new EnumMap<>(BeatBlockShortcutId.class);
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			drafts.put(id, id.defaultChord());
		}
		drafts.put(BeatBlockShortcutId.SAVE_PROJECT, "Ctrl+K");
		assertFalse(ShortcutValidation.validate(drafts).ok());

		drafts.put(BeatBlockShortcutId.SAVE_PROJECT, "Ctrl+S");
		drafts.put(BeatBlockShortcutId.OPEN_PROJECT, "Ctrl+S");
		assertFalse(ShortcutValidation.validate(drafts).ok());

		drafts.put(BeatBlockShortcutId.OPEN_PROJECT, "Ctrl+Shift+O");
		assertTrue(ShortcutValidation.validate(drafts).ok());
	}

	@Test
	void presenterRejectsInvalidShortcutsWithoutPersisting() {
		PreferencesPresenter presenter = new PreferencesPresenter();
		EnumMap<BeatBlockShortcutId, String> drafts = defaults();
		drafts.put(BeatBlockShortcutId.DELETE, "Ctrl+F");
		assertFalse(presenter.saveShortcuts(drafts).ok());
		assertEquals("Delete", UiPreferences.shortcut(BeatBlockShortcutId.DELETE));
	}

	@Test
	void presenterSavesNormalizedShortcutsOnce() {
		PreferencesPresenter presenter = new PreferencesPresenter();
		EnumMap<BeatBlockShortcutId, String> drafts = defaults();
		drafts.put(BeatBlockShortcutId.SAVE_PROJECT, "ctrl+s");
		assertTrue(presenter.saveShortcuts(drafts).ok());
		assertEquals("Ctrl+S", UiPreferences.shortcut(BeatBlockShortcutId.SAVE_PROJECT));
		assertTrue(Files.isRegularFile(tempDir.resolve("ui.json")));
	}

	@Test
	void corruptUiJsonSelfHealsOnNextSave() throws Exception {
		Path ui = tempDir.resolve("ui.json");
		Files.writeString(ui, "{not-json", StandardCharsets.UTF_8);
		UiPreferences.resetForTests();
		assertEquals(UiTheme.DARK, UiPreferences.theme());
		assertTrue(UiPreferences.wasCorruptOnLoad());
		assertTrue(UiPreferences.setTheme(UiTheme.LIGHT));
		String saved = Files.readString(ui, StandardCharsets.UTF_8);
		assertTrue(saved.contains("light"));
		assertFalse(UiPreferences.wasCorruptOnLoad());
	}

	@Test
	void batchReplaceDoesNotLeavePartialShortcuts() {
		PreferencesPresenter presenter = new PreferencesPresenter();
		EnumMap<BeatBlockShortcutId, String> drafts = defaults();
		drafts.put(BeatBlockShortcutId.COPY, "Ctrl+C");
		drafts.put(BeatBlockShortcutId.CUT, "Ctrl+X");
		assertTrue(presenter.saveShortcuts(drafts).ok());
		assertTrue(presenter.resetShortcuts().ok());
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			assertEquals(id.defaultChord(), UiPreferences.shortcut(id));
		}
	}

	private static EnumMap<BeatBlockShortcutId, String> defaults() {
		EnumMap<BeatBlockShortcutId, String> drafts = new EnumMap<>(BeatBlockShortcutId.class);
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			drafts.put(id, id.defaultChord());
		}
		return drafts;
	}
}
