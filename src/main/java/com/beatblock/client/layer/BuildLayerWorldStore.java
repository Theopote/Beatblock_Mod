package com.beatblock.client.layer;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerGroup;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.BuildLayerWorldPersistence;
import com.beatblock.runtime.BeatBlockContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 将建造图层自动保存到当前世界存档（{@code <save>/beatblock/layers/<dimension>.json}）。
 * <p>
 * 单人/本地集成服务端下写入世界目录；切换维度时分别加载对应文件。
 */
public final class BuildLayerWorldStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildLayerWorldStore.class);
	private static final long SAVE_DEBOUNCE_MS = 1_500L;

	private static RegistryKey<World> activeDimension;
	private static int lastSavedHash = Integer.MIN_VALUE;
	private static long lastChangeAtMs;
	private static boolean dirty;

	private BuildLayerWorldStore() {
	}

	public static void onWorldJoined(MinecraftClient client) {
		if (client == null || client.world == null) {
			return;
		}
		loadForWorld(client, client.world);
	}

	public static void onWorldLeft(MinecraftClient client) {
		flushNow(client);
		purgeActiveManager();
		activeDimension = null;
		lastSavedHash = Integer.MIN_VALUE;
		dirty = false;
	}

	public static void onClientTick(MinecraftClient client) {
		if (client == null || client.world == null) {
			return;
		}
		RegistryKey<World> dimension = client.world.getRegistryKey();
		if (activeDimension != null && !activeDimension.equals(dimension)) {
			BuildLayerManager manager = layerManager();
			if (manager != null) {
				saveForDimension(client, activeDimension, manager);
			}
			loadForWorld(client, client.world);
			return;
		}
		BuildLayerManager manager = layerManager();
		if (manager == null) {
			return;
		}
		int hash = hashManager(manager);
		long now = System.currentTimeMillis();
		if (hash != lastSavedHash) {
			dirty = true;
			lastChangeAtMs = now;
		}
		if (dirty && now - lastChangeAtMs >= SAVE_DEBOUNCE_MS) {
			saveForWorld(client, client.world, manager);
		}
	}

	public static void flushNow(@Nullable MinecraftClient client) {
		if (client == null || activeDimension == null) {
			return;
		}
		BuildLayerManager manager = layerManager();
		if (manager == null) {
			return;
		}
		saveForDimension(client, activeDimension, manager);
	}

	private static void loadForWorld(MinecraftClient client, World world) {
		BuildLayerManager manager = layerManager();
		if (manager == null || world == null) {
			return;
		}
		Path worldRoot = resolveWorldRoot(client);
		if (worldRoot == null) {
			manager.purgeAllLayers();
			activeDimension = world.getRegistryKey();
			lastSavedHash = hashManager(manager);
			dirty = false;
			return;
		}
		Path file = BuildLayerWorldPersistence.layerFileForDimension(worldRoot, world.getRegistryKey());
		try {
			if (BuildLayerWorldPersistence.load(file, manager)) {
				LOGGER.info("BeatBlock: loaded build layers from {}", file);
			} else {
				manager.purgeAllLayers();
				LOGGER.debug("BeatBlock: no saved build layers at {}", file);
			}
			manager.applyPersistedWorldState(world);
		} catch (IOException e) {
			LOGGER.warn("BeatBlock: failed to load build layers from {}: {}", file, e.getMessage());
			manager.purgeAllLayers();
		}
		activeDimension = world.getRegistryKey();
		lastSavedHash = hashManager(manager);
		dirty = false;
	}

	private static void saveForWorld(MinecraftClient client, World world, BuildLayerManager manager) {
		if (world == null || manager == null) {
			return;
		}
		saveForDimension(client, world.getRegistryKey(), manager);
	}

	private static void saveForDimension(
		MinecraftClient client,
		RegistryKey<World> dimension,
		BuildLayerManager manager
	) {
		Path worldRoot = resolveWorldRoot(client);
		if (worldRoot == null || dimension == null || manager == null) {
			return;
		}
		Path file = BuildLayerWorldPersistence.layerFileForDimension(worldRoot, dimension);
		try {
			BuildLayerWorldPersistence.save(file, dimension, manager);
			lastSavedHash = hashManager(manager);
			dirty = false;
			LOGGER.debug("BeatBlock: saved build layers to {}", file);
		} catch (IOException e) {
			LOGGER.warn("BeatBlock: failed to save build layers to {}: {}", file, e.getMessage());
		}
	}

	private static void purgeActiveManager() {
		BuildLayerManager manager = layerManager();
		if (manager != null) {
			manager.purgeAllLayers();
		}
	}

	private static @Nullable BuildLayerManager layerManager() {
		try {
			BeatBlockContext ctx = BeatBlock.getContext();
			return ctx != null ? ctx.buildLayerManager() : null;
		} catch (IllegalStateException ignored) {
			return null;
		}
	}

	private static @Nullable Path resolveWorldRoot(MinecraftClient client) {
		if (client == null) {
			return null;
		}
		MinecraftServer server = client.getServer();
		if (server == null) {
			return null;
		}
		return server.getSavePath(WorldSavePath.ROOT);
	}

	private static int hashManager(BuildLayerManager manager) {
		int hash = 1;
		for (BuildLayerGroup group : manager.getAllGroups()) {
			hash = 31 * hash + group.getId().hashCode();
			hash = 31 * hash + group.getName().hashCode();
			hash = 31 * hash + group.getColorArgb();
		}
		for (BuildLayer layer : manager.getAll()) {
			hash = 31 * hash + layer.getId().hashCode();
			hash = 31 * hash + layer.getName().hashCode();
			hash = 31 * hash + layer.getState().hashCode();
			hash = 31 * hash + layer.getColorArgb();
			hash = 31 * hash + Objects.hashCode(layer.getGroupId());
			hash = 31 * hash + Objects.hashCode(layer.getBoundClipId());
			hash = 31 * hash + layer.getStageObject().getBlocks().size();
		}
		return hash;
	}

	static void resetForTests() {
		activeDimension = null;
		lastSavedHash = Integer.MIN_VALUE;
		lastChangeAtMs = 0L;
		dirty = false;
	}
}
