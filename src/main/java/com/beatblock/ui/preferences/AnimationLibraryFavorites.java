package com.beatblock.ui.preferences;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/** 轻量 UI 偏好：动画库收藏等，持久化到 config/beatblock/ui.json。 */
public final class AnimationLibraryFavorites {

	private static final Logger LOGGER = LoggerFactory.getLogger(AnimationLibraryFavorites.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String ROOT_KEY = "animationFavorites";

	private static final LinkedHashSet<String> favorites = new LinkedHashSet<>();
	private static boolean loaded;

	private AnimationLibraryFavorites() {
	}

	public static Set<String> all() {
		ensureLoaded();
		return Set.copyOf(favorites);
	}

	public static boolean isFavorite(String presetId) {
		if (presetId == null || presetId.isBlank()) {
			return false;
		}
		ensureLoaded();
		return favorites.contains(presetId);
	}

	public static void toggle(String presetId) {
		if (presetId == null || presetId.isBlank()) {
			return;
		}
		ensureLoaded();
		if (favorites.contains(presetId)) {
			favorites.remove(presetId);
		} else {
			favorites.add(presetId);
		}
		save();
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
			String json = Files.readString(path, StandardCharsets.UTF_8);
			JsonObject root = JsonParser.parseString(json).getAsJsonObject();
			if (!root.has(ROOT_KEY) || !root.get(ROOT_KEY).isJsonArray()) {
				return;
			}
			favorites.clear();
			for (JsonElement element : root.getAsJsonArray(ROOT_KEY)) {
				if (element.isJsonPrimitive()) {
					String id = element.getAsString();
					if (id != null && !id.isBlank()) {
						favorites.add(id);
					}
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to load animation favorites from {}", path, e);
		}
	}

	private static void save() {
		Path path = uiConfigPath();
		try {
			JsonObject root;
			if (Files.isRegularFile(path)) {
				root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
			} else {
				root = new JsonObject();
				Files.createDirectories(path.getParent());
			}
			JsonArray array = new JsonArray();
			for (String id : favorites) {
				array.add(id);
			}
			root.add(ROOT_KEY, array);
			Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("Failed to save animation favorites to {}", path, e);
		}
	}

	private static Path uiConfigPath() {
		return FabricLoader.getInstance().getGameDir()
			.resolve("config").resolve("beatblock").resolve("ui.json");
	}
}
