package com.beatblock.ui.preferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import imgui.flag.ImGuiCol;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * UI preferences store: theme + shortcuts + onboarding flags → {@code config/beatblock/ui.json}.
 * Saves are atomic; corrupt files recover on next save without requiring a parse of the bad file.
 */
public final class UiPreferences {

	private static final Logger LOGGER = LoggerFactory.getLogger(UiPreferences.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String THEME_KEY = "uiTheme";
	private static final String SHORTCUTS_KEY = "shortcuts";
	private static final String PYTHON_SETUP_ACKNOWLEDGED_KEY = "pythonSetupAcknowledged";
	private static final String QUICK_START_WIZARD_ACKNOWLEDGED_KEY = "quickStartWizardAcknowledged";

	private static UiTheme theme = UiTheme.DARK;
	private static final EnumMap<BeatBlockShortcutId, String> shortcuts = new EnumMap<>(BeatBlockShortcutId.class);
	private static boolean pythonSetupAcknowledged;
	private static boolean quickStartWizardAcknowledged;
	private static boolean loaded;
	/** When load failed or file was corrupt — next save writes a fresh root. */
	private static boolean corruptRecovered;

	private UiPreferences() {
	}

	/** Test-only reset of in-memory state. */
	static void resetForTests() {
		theme = UiTheme.DARK;
		shortcuts.clear();
		pythonSetupAcknowledged = false;
		quickStartWizardAcknowledged = false;
		loaded = false;
		corruptRecovered = false;
	}

	public static UiTheme theme() {
		ensureLoaded();
		return theme;
	}

	/** Apply theme in-memory and persist. @return false if disk write failed (runtime theme still applied). */
	public static boolean setTheme(UiTheme value) {
		ensureLoaded();
		theme = value != null ? value : UiTheme.DARK;
		return save();
	}

	public static String shortcut(BeatBlockShortcutId id) {
		ensureLoaded();
		return shortcuts.getOrDefault(id, id.defaultChord());
	}

	/**
	 * Replace custom shortcuts in one shot and persist once.
	 * @param custom only non-default chords; empty map means all defaults
	 */
	public static boolean replaceShortcuts(Map<BeatBlockShortcutId, String> custom) {
		ensureLoaded();
		shortcuts.clear();
		if (custom != null) {
			for (Map.Entry<BeatBlockShortcutId, String> entry : custom.entrySet()) {
				if (entry.getKey() == null) continue;
				String chord = entry.getValue();
				if (chord == null || chord.isBlank()) continue;
				ShortcutChord parsed = ShortcutChord.parse(chord);
				if (parsed == null) continue;
				String normalized = parsed.normalize();
				if (!normalized.equalsIgnoreCase(entry.getKey().defaultChord())) {
					shortcuts.put(entry.getKey(), normalized);
				}
			}
		}
		return save();
	}

	public static boolean setShortcut(BeatBlockShortcutId id, String chord) {
		if (id == null) {
			return false;
		}
		ensureLoaded();
		if (chord == null || chord.isBlank() || chord.equalsIgnoreCase(id.defaultChord())) {
			shortcuts.remove(id);
		} else {
			ShortcutChord parsed = ShortcutChord.parse(chord);
			if (parsed == null) {
				return false;
			}
			shortcuts.put(id, parsed.normalize());
		}
		return save();
	}

	public static boolean resetShortcuts() {
		ensureLoaded();
		shortcuts.clear();
		return save();
	}

	/** Theme + shortcuts back to defaults (acknowledgements unchanged). */
	public static boolean resetAppearanceAndShortcuts() {
		ensureLoaded();
		theme = UiTheme.DARK;
		shortcuts.clear();
		return save();
	}

	public static boolean isPythonSetupAcknowledged() {
		ensureLoaded();
		return pythonSetupAcknowledged;
	}

	public static boolean setPythonSetupAcknowledged(boolean acknowledged) {
		ensureLoaded();
		if (pythonSetupAcknowledged == acknowledged) {
			return true;
		}
		pythonSetupAcknowledged = acknowledged;
		return save();
	}

	public static boolean isQuickStartWizardAcknowledged() {
		ensureLoaded();
		return quickStartWizardAcknowledged;
	}

	public static boolean setQuickStartWizardAcknowledged(boolean acknowledged) {
		ensureLoaded();
		if (quickStartWizardAcknowledged == acknowledged) {
			return true;
		}
		quickStartWizardAcknowledged = acknowledged;
		return save();
	}

	public static Map<BeatBlockShortcutId, String> allShortcuts() {
		ensureLoaded();
		Map<BeatBlockShortcutId, String> out = new EnumMap<>(BeatBlockShortcutId.class);
		for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
			out.put(id, shortcut(id));
		}
		return out;
	}

	public static boolean wasCorruptOnLoad() {
		ensureLoaded();
		return corruptRecovered;
	}

	public static void pushPanelThemeColors() {
		UiThemeColors colors = UiThemeColors.forTheme(theme());
		pushColor(ImGuiCol.WindowBg, colors.windowBg());
		pushColor(ImGuiCol.Text, colors.text());
		pushColor(ImGuiCol.TitleBg, colors.titleBg());
		pushColor(ImGuiCol.TitleBgActive, colors.titleBgActive());
		pushColor(ImGuiCol.TitleBgCollapsed, colors.titleBgCollapsed());
	}

	private static void pushColor(int colorIndex, float[] rgba) {
		imgui.ImGui.pushStyleColor(colorIndex, rgba[0], rgba[1], rgba[2], rgba[3]);
	}

	public static void popPanelThemeColors() {
		imgui.ImGui.popStyleColor(5);
	}

	private static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		Path path = uiConfigPath();
		if (!Files.isRegularFile(path)) {
			return;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			applyRoot(root);
		} catch (Exception e) {
			corruptRecovered = true;
			LOGGER.warn("Failed to load UI preferences from {} — using defaults; next save will rewrite file", path, e);
		}
	}

	private static void applyRoot(JsonObject root) {
		if (root.has(THEME_KEY)) {
			theme = UiTheme.fromId(root.get(THEME_KEY).getAsString());
		}
		if (root.has(SHORTCUTS_KEY) && root.get(SHORTCUTS_KEY).isJsonObject()) {
			JsonObject map = root.getAsJsonObject(SHORTCUTS_KEY);
			for (BeatBlockShortcutId id : BeatBlockShortcutId.values()) {
				if (map.has(id.id()) && map.get(id.id()).isJsonPrimitive()) {
					String raw = map.get(id.id()).getAsString();
					ShortcutChord chord = ShortcutChord.parse(raw);
					if (chord != null) {
						shortcuts.put(id, chord.normalize());
					}
				}
			}
		}
		if (root.has(PYTHON_SETUP_ACKNOWLEDGED_KEY)) {
			pythonSetupAcknowledged = root.get(PYTHON_SETUP_ACKNOWLEDGED_KEY).getAsBoolean();
		}
		if (root.has(QUICK_START_WIZARD_ACKNOWLEDGED_KEY)) {
			quickStartWizardAcknowledged = root.get(QUICK_START_WIZARD_ACKNOWLEDGED_KEY).getAsBoolean();
		}
	}

	/**
	 * Persist current in-memory preferences atomically.
	 * @return true on success
	 */
	public static boolean save() {
		Path path = uiConfigPath();
		try {
			JsonObject root = readMergeRoot(path);
			root.addProperty(THEME_KEY, theme.id());
			JsonObject map = new JsonObject();
			for (Map.Entry<BeatBlockShortcutId, String> entry : shortcuts.entrySet()) {
				map.addProperty(entry.getKey().id(), entry.getValue());
			}
			root.add(SHORTCUTS_KEY, map);
			root.addProperty(PYTHON_SETUP_ACKNOWLEDGED_KEY, pythonSetupAcknowledged);
			root.addProperty(QUICK_START_WIZARD_ACKNOWLEDGED_KEY, quickStartWizardAcknowledged);
			AtomicConfigFiles.writeAtomically(path, GSON.toJson(root));
			corruptRecovered = false;
			return true;
		} catch (Exception e) {
			LOGGER.warn("Failed to save UI preferences to {}", path, e);
			return false;
		}
	}

	/**
	 * Prefer merging unknown keys from an existing valid file.
	 * If corrupt or {@link #corruptRecovered}, start a fresh root so save can self-heal.
	 */
	private static JsonObject readMergeRoot(Path path) {
		if (corruptRecovered || !Files.isRegularFile(path)) {
			return new JsonObject();
		}
		try {
			return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			LOGGER.warn("Existing ui.json unreadable during save — writing fresh preferences root", e);
			corruptRecovered = true;
			return new JsonObject();
		}
	}

	static Path uiConfigPath() {
		String overrideDir = System.getProperty("beatblock.test.configDir");
		if (overrideDir != null && !overrideDir.isBlank()) {
			return Path.of(overrideDir).resolve("ui.json");
		}
		return FabricLoader.getInstance().getGameDir()
			.resolve("config").resolve("beatblock").resolve("ui.json");
	}
}
