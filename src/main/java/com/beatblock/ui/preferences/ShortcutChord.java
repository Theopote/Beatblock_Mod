package com.beatblock.ui.preferences;

import imgui.ImGui;
import imgui.flag.ImGuiKey;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Shared shortcut chord authority for Preferences validation and runtime dispatch.
 * Invalid tokens parse to {@code null} (same as runtime: never fire).
 */
public final class ShortcutChord {

	private static final int NATIVE_KEY_OFFSET = 512;
	private static final Set<Integer> NATIVE_KEYS_DOWN = new HashSet<>();

	private final boolean ctrl;
	private final boolean shift;
	private final boolean alt;
	private final int key;
	private final String display;

	private ShortcutChord(boolean ctrl, boolean shift, boolean alt, int key, String display) {
		this.ctrl = ctrl;
		this.shift = shift;
		this.alt = alt;
		this.key = key;
		this.display = display;
	}

	public boolean ctrl() {
		return ctrl;
	}

	public boolean shift() {
		return shift;
	}

	public boolean alt() {
		return alt;
	}

	/** Canonical display form, e.g. {@code Ctrl+Shift+O}. */
	public String display() {
		return display;
	}

	/** Alias for conflict maps — same as {@link #display()}. */
	public String normalize() {
		return display;
	}

	public boolean isValid() {
		return key >= 0;
	}

	/**
	 * Parse a chord string. Returns {@code null} when empty or when the key token is unsupported.
	 */
	public static @Nullable ShortcutChord parse(@Nullable String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		boolean ctrl = false;
		boolean shift = false;
		boolean alt = false;
		int key = -1;
		String keyToken = null;
		for (String part : raw.split("\\+")) {
			String token = part.trim();
			if (token.isEmpty()) {
				continue;
			}
			String upper = token.toUpperCase(Locale.ROOT);
			switch (upper) {
				case "CTRL", "CONTROL" -> ctrl = true;
				case "SHIFT" -> shift = true;
				case "ALT" -> alt = true;
				default -> {
					key = keyFromToken(upper);
					keyToken = upper;
				}
			}
		}
		if (key < 0 || keyToken == null) {
			return null;
		}
		return new ShortcutChord(ctrl, shift, alt, key, buildDisplay(ctrl, shift, alt, keyToken));
	}

	public static boolean isValidChord(@Nullable String raw) {
		return parse(raw) != null;
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

	private static String buildDisplay(boolean ctrl, boolean shift, boolean alt, String keyToken) {
		StringBuilder sb = new StringBuilder();
		if (ctrl) sb.append("Ctrl+");
		if (shift) sb.append("Shift+");
		if (alt) sb.append("Alt+");
		String displayKey = switch (keyToken) {
			case "DELETE", "DEL" -> "Delete";
			default -> keyToken.length() == 1 ? keyToken : keyToken;
		};
		if (displayKey.length() == 1) {
			displayKey = displayKey.toUpperCase(Locale.ROOT);
		} else if ("Delete".equalsIgnoreCase(displayKey)) {
			displayKey = "Delete";
		}
		sb.append(displayKey);
		return sb.toString();
	}

	private static int keyFromToken(String token) {
		return switch (token) {
			case "S" -> NATIVE_KEY_OFFSET + GLFW.GLFW_KEY_S;
			case "O" -> NATIVE_KEY_OFFSET + GLFW.GLFW_KEY_O;
			case "D" -> NATIVE_KEY_OFFSET + GLFW.GLFW_KEY_D;
			case "E" -> NATIVE_KEY_OFFSET + GLFW.GLFW_KEY_E;
			case "M" -> NATIVE_KEY_OFFSET + GLFW.GLFW_KEY_M;
			case "Z" -> ImGuiKey.Z;
			case "Y" -> ImGuiKey.Y;
			case "C" -> ImGuiKey.C;
			case "X" -> ImGuiKey.X;
			case "V" -> ImGuiKey.V;
			case "DELETE", "DEL" -> ImGuiKey.Delete;
			default -> -1;
		};
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ShortcutChord that)) return false;
		return ctrl == that.ctrl && shift == that.shift && alt == that.alt && key == that.key;
	}

	@Override
	public int hashCode() {
		return Objects.hash(ctrl, shift, alt, key);
	}

	@Override
	public String toString() {
		return display;
	}
}
