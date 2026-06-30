package com.beatblock.selection.preset;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 选区预设的加载/保存入口（config/beatblock/selection_presets.json）。
 */
public final class SelectionPresetStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(SelectionPresetStore.class);

	private static volatile boolean loaded;

	private SelectionPresetStore() {
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		synchronized (SelectionPresetStore.class) {
			if (loaded) {
				return;
			}
			try {
				Path path = presetFilePath();
				SelectionPresetPersistence.load(path, SelectionPresetManager.getInstance());
			} catch (IOException e) {
				LOGGER.warn("BeatBlock: failed to load selection presets: {}", e.getMessage());
			}
			loaded = true;
		}
	}

	public static void persist() {
		ensureLoaded();
		try {
			SelectionPresetPersistence.save(presetFilePath(), SelectionPresetManager.getInstance());
		} catch (IOException e) {
			LOGGER.warn("BeatBlock: failed to save selection presets: {}", e.getMessage());
		}
	}

	public static Path presetFilePath() {
		return SelectionPresetPersistence.getDefaultPath(FabricLoader.getInstance().getGameDir());
	}

	static void resetLoadedStateForTests() {
		loaded = false;
		SelectionPresetManager.getInstance().clear();
	}
}
