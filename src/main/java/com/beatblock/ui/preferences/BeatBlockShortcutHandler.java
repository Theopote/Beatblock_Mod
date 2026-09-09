package com.beatblock.ui.preferences;

import com.beatblock.ui.presenter.TimelineActionDispatcher;
import com.beatblock.ui.presenter.TimelineActionId;
import imgui.ImGui;

/** 全局快捷键处理：在 ImGui 未捕获键盘时响应可配置组合键（语义权威：{@link ShortcutChord}）。 */
public final class BeatBlockShortcutHandler {

	public interface MenuActions {
		void openImportMusic();
		void saveProject();
		void openProject();
		void generateRhythmDrop();
	}

	private BeatBlockShortcutHandler() {
	}

	public static void processGlobalShortcuts(
		TimelineActionDispatcher actions,
		MenuActions menuActions
	) {
		if (ImGui.getIO() == null || ImGui.getIO().getWantCaptureKeyboard()) {
			return;
		}
		if (actions == null) {
			return;
		}
		if (isPressed(BeatBlockShortcutId.UNDO)) {
			actions.execute(TimelineActionId.UNDO);
		}
		if (isPressed(BeatBlockShortcutId.REDO)) {
			actions.execute(TimelineActionId.REDO);
		}
		if (menuActions != null) {
			if (isPressed(BeatBlockShortcutId.IMPORT_MUSIC)) menuActions.openImportMusic();
			if (isPressed(BeatBlockShortcutId.SAVE_PROJECT)) menuActions.saveProject();
			if (isPressed(BeatBlockShortcutId.OPEN_PROJECT)) menuActions.openProject();
			if (isPressed(BeatBlockShortcutId.GENERATE_RHYTHM_DROP)) menuActions.generateRhythmDrop();
		}
		if (isPressed(BeatBlockShortcutId.COPY)) actions.execute(TimelineActionId.COPY);
		if (isPressed(BeatBlockShortcutId.CUT)) actions.execute(TimelineActionId.CUT);
		if (isPressed(BeatBlockShortcutId.PASTE)) actions.execute(TimelineActionId.PASTE_AT_PLAYHEAD);
		if (isPressed(BeatBlockShortcutId.DUPLICATE)) actions.execute(TimelineActionId.DUPLICATE);
		if (isPressed(BeatBlockShortcutId.SPLIT)) actions.execute(TimelineActionId.SPLIT_AT_PLAYHEAD);
		if (isPressed(BeatBlockShortcutId.DELETE)) actions.execute(TimelineActionId.DELETE);
		if (isPressed(BeatBlockShortcutId.ADD_MARKER)) actions.execute(TimelineActionId.ADD_MARKER_AT_PLAYHEAD);
	}

	public static boolean isPressed(BeatBlockShortcutId id) {
		ShortcutChord chord = ShortcutChord.parse(UiPreferences.shortcut(id));
		return chord != null && chord.isPressed();
	}
}
