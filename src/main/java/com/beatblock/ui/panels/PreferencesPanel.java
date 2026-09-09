package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.preferences.BeatBlockShortcutId;
import com.beatblock.ui.preferences.UiTheme;
import com.beatblock.ui.presenter.PreferencesPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.PresenterResult;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.EnumMap;
import java.util.Map;

/** Preferences Creator Surface: Appearance + Shortcuts (user-level only). */
public final class PreferencesPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final int CHORD_CAPACITY = 64;

	private final PreferencesPresenter presenter;
	private final ImInt themeIndex = new ImInt(0);
	private final EnumMap<BeatBlockShortcutId, ImString> shortcutBuffers = new EnumMap<>(BeatBlockShortcutId.class);
	private boolean buffersInitialized;

	public PreferencesPanel() {
		this(PresenterFactories.preferencesPresenter());
	}

	PreferencesPanel(PreferencesPresenter presenter) {
		this.presenter = presenter != null ? presenter : new PreferencesPresenter();
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			shortcutBuffers.put(id, new ImString(CHORD_CAPACITY));
		}
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.preferencesWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.preferencesWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			ensureBuffers();
			ImGui.text(BBTexts.get("beatblock.preferences.title"));
			ImGui.separator();
			if (ImGui.beginTabBar("##preferencesTabs")) {
				if (ImGui.beginTabItem(BBTexts.get("beatblock.preferences.tab.theme"))) {
					renderThemeTab();
					ImGui.endTabItem();
				}
				if (ImGui.beginTabItem(BBTexts.get("beatblock.preferences.tab.shortcuts"))) {
					renderShortcutsTab();
					ImGui.endTabItem();
				}
				ImGui.endTabBar();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.preferencesWindow());
		}
	}

	private void ensureBuffers() {
		if (buffersInitialized) {
			return;
		}
		buffersInitialized = true;
		reloadFromStore();
	}

	private void reloadFromStore() {
		themeIndex.set(themeToIndex(presenter.theme()));
		Map<BeatBlockShortcutId, String> all = presenter.allShortcuts();
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			shortcutBuffers.get(id).set(all.getOrDefault(id, id.defaultChord()));
		}
	}

	private void renderThemeTab() {
		ImGui.textWrapped(BBTexts.get("beatblock.preferences.theme.desc"));
		ImGui.spacing();
		String[] labels = BBTexts.labels(
			"beatblock.preferences.theme.dark",
			"beatblock.preferences.theme.light",
			"beatblock.preferences.theme.high_contrast"
		);
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo(BBTexts.get("beatblock.preferences.theme.label") + "##uiTheme", themeIndex, labels)) {
			showResult(presenter.applyTheme(indexToTheme(themeIndex.get())));
		}
		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.preferences.reset_defaults") + "##resetPrefs")) {
			showResult(presenter.resetAppearanceAndShortcuts());
			reloadFromStore();
		}
	}

	private void renderShortcutsTab() {
		ImGui.textWrapped(BBTexts.get("beatblock.preferences.shortcuts.desc"));
		ImGui.spacing();
		if (ImGui.beginChild("##shortcutList", 0, -40f, true)) {
			for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
				ImGui.text(BBTexts.get("beatblock.preferences.shortcut." + id.id()));
				ImGui.setNextItemWidth(-1f);
				ImGui.inputText("##shortcut_" + id.id(), shortcutBuffers.get(id));
			}
		}
		ImGui.endChild();
		if (ImGui.button(BBTexts.get("beatblock.preferences.shortcuts.save") + "##saveShortcuts", -1f, 0f)) {
			EnumMap<BeatBlockShortcutId, String> drafts = new EnumMap<>(BeatBlockShortcutId.class);
			for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
				drafts.put(id, shortcutBuffers.get(id).get());
			}
			PresenterResult result = presenter.saveShortcuts(drafts);
			showResult(result);
			if (result.ok()) {
				reloadFromStore();
			}
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.preferences.shortcuts.reset") + "##resetShortcuts")) {
			showResult(presenter.resetShortcuts());
			reloadFromStore();
		}
	}

	private static void showResult(PresenterResult result) {
		if (result == null || result.messageOrEmpty().isBlank()) return;
		if (result.ok()) {
			ToastNotificationSystem.showSuccess(result.messageOrEmpty());
		} else {
			ToastNotificationSystem.showError(result.messageOrEmpty());
		}
	}

	private static int themeToIndex(UiTheme theme) {
		return switch (theme) {
			case LIGHT -> 1;
			case HIGH_CONTRAST -> 2;
			default -> 0;
		};
	}

	private static UiTheme indexToTheme(int index) {
		return switch (index) {
			case 1 -> UiTheme.LIGHT;
			case 2 -> UiTheme.HIGH_CONTRAST;
			default -> UiTheme.DARK;
		};
	}
}
