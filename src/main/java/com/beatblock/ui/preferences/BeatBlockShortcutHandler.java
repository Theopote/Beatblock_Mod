package com.beatblock.ui.preferences;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.presenter.MenuBarPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiKey;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

/** 全局快捷键处理：在 ImGui 未捕获键盘时响应可配置组合键。 */
public final class BeatBlockShortcutHandler {

	private static final int NATIVE_KEY_OFFSET = 512;
	private static final Set<Integer> NATIVE_KEYS_DOWN = new HashSet<>();

	public interface MenuActions {
		void openImportMusic();
		void saveProject();
		void openProject();
		void generateRhythmDrop();
	}

	private BeatBlockShortcutHandler() {
	}

	public static void processGlobalShortcuts(MenuActions menuActions) {
		if (ImGui.getIO() == null || ImGui.getIO().getWantCaptureKeyboard()) {
			return;
		}
		MenuBarPresenter menu = PresenterFactories.menuBarPresenter();
		TimelineEditor editor = PresenterFactories.timelineEditorPresenter().editorOrNull();
		if (isPressed(BeatBlockShortcutId.UNDO)) {
			menu.undo();
		}
		if (isPressed(BeatBlockShortcutId.REDO)) {
			menu.redo();
		}
		if (menuActions != null) {
			if (isPressed(BeatBlockShortcutId.IMPORT_MUSIC)) menuActions.openImportMusic();
			if (isPressed(BeatBlockShortcutId.SAVE_PROJECT)) menuActions.saveProject();
			if (isPressed(BeatBlockShortcutId.OPEN_PROJECT)) menuActions.openProject();
			if (isPressed(BeatBlockShortcutId.GENERATE_RHYTHM_DROP)) menuActions.generateRhythmDrop();
		}
		if (editor != null) {
			if (isPressed(BeatBlockShortcutId.COPY)) {
				editor.copySelectedEvents();
			}
			if (isPressed(BeatBlockShortcutId.CUT)) {
				editor.cutSelectedEvents();
			}
			if (isPressed(BeatBlockShortcutId.PASTE)) {
				editor.pasteClipboardAtPlayhead();
			}
			if (isPressed(BeatBlockShortcutId.DELETE)) {
				editor.deleteSelectedEntries();
			}
		}
	}

	public static boolean isPressed(BeatBlockShortcutId id) {
		Chord chord = Chord.parse(UiPreferences.shortcut(id));
		return chord != null && chord.isPressed();
	}

	private record Chord(boolean ctrl, boolean shift, boolean alt, int key) {

		static Chord parse(String raw) {
			if (raw == null || raw.isBlank()) {
				return null;
			}
			boolean ctrl = false;
			boolean shift = false;
			boolean alt = false;
			int key = -1;
			for (String part : raw.split("\\+")) {
				String token = part.trim();
				if (token.isEmpty()) {
					continue;
				}
				String upper = token.toUpperCase();
				switch (upper) {
					case "CTRL", "CONTROL" -> ctrl = true;
					case "SHIFT" -> shift = true;
					case "ALT" -> alt = true;
					default -> key = keyFromToken(upper);
				}
			}
			return key >= 0 ? new Chord(ctrl, shift, alt, key) : null;
		}

		boolean isPressed() {
			var io = ImGui.getIO();
			if (io.getKeyCtrl() != ctrl || io.getKeyShift() != shift || io.getKeyAlt() != alt) {
				return false;
			}
			if (key >= NATIVE_KEY_OFFSET) {
				int nativeKey = key - NATIVE_KEY_OFFSET;
				boolean down = io.getKeysDown(nativeKey);
				if (!down) {
					NATIVE_KEYS_DOWN.remove(nativeKey);
					return false;
				}
				return NATIVE_KEYS_DOWN.add(nativeKey);
			}
			return ImGui.isKeyPressed(key);
		}

		private static int keyFromToken(String token) {
			return switch (token) {
				case "S" -> NATIVE_KEY_OFFSET + GLFW.GLFW_KEY_S;
				case "O" -> NATIVE_KEY_OFFSET + GLFW.GLFW_KEY_O;
				case "D" -> NATIVE_KEY_OFFSET + GLFW.GLFW_KEY_D;
				case "Z" -> ImGuiKey.Z;
				case "Y" -> ImGuiKey.Y;
				case "C" -> ImGuiKey.C;
				case "X" -> ImGuiKey.X;
				case "V" -> ImGuiKey.V;
				case "DELETE", "DEL" -> ImGuiKey.Delete;
				default -> -1;
			};
		}
	}
}
