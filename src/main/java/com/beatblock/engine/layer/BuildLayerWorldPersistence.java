package com.beatblock.engine.layer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 建造图层在世界存档目录中的 JSON 读写（按维度分文件）。 */
public final class BuildLayerWorldPersistence {

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildLayerWorldPersistence.class);
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int VERSION = 1;

	private BuildLayerWorldPersistence() {
	}

	public static Path layerFileForDimension(Path worldRoot, RegistryKey<World> dimension) {
		String key = dimensionFileKey(dimension);
		return worldRoot.resolve("beatblock").resolve("layers").resolve(key + ".json");
	}

	public static String dimensionFileKey(RegistryKey<World> dimension) {
		if (dimension == null) {
			return "unknown";
		}
		return dimension.getValue().toString().replace(':', '_');
	}

	public static void save(Path filePath, RegistryKey<World> dimension, BuildLayerManager manager) throws IOException {
		if (filePath == null || manager == null) {
			return;
		}
		JsonObject root = new JsonObject();
		root.addProperty("version", VERSION);
		if (dimension != null) {
			root.addProperty("dimension", dimension.getValue().toString());
		}
		root.add("buildLayers", BuildLayerPersistence.toJson(manager));
		root.add("buildLayerGroups", BuildLayerPersistence.groupsToJson(manager));

		Path parent = filePath.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Path temporary = filePath.resolveSibling(filePath.getFileName() + ".tmp");
		Path backup = backupPath(filePath);
		try {
			Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
			if (Files.exists(filePath)) {
				Files.copy(filePath, backup, StandardCopyOption.REPLACE_EXISTING);
			}
			moveReplacing(temporary, filePath);
		} finally {
			Files.deleteIfExists(temporary);
		}
	}

	public static boolean load(Path filePath, BuildLayerManager manager) throws IOException {
		if (filePath == null || manager == null || !Files.exists(filePath)) {
			return false;
		}
		JsonObject root;
		try {
			root = readRoot(filePath);
		} catch (RuntimeException primaryFailure) {
			Path backup = backupPath(filePath);
			if (!Files.exists(backup)) {
				throw new IOException("Invalid build layer file: " + filePath, primaryFailure);
			}
			LOGGER.warn("Invalid build layer file {}; loading backup {}", filePath, backup);
			try {
				root = readRoot(backup);
			} catch (RuntimeException backupFailure) {
				IOException failure = new IOException("Invalid build layer file and backup: " + filePath, primaryFailure);
				failure.addSuppressed(backupFailure);
				throw failure;
			}
		}
		int version = root.has("version") ? root.get("version").getAsInt() : 1;
		if (version > VERSION) {
			LOGGER.warn("BeatBlock: unsupported build layer world file version {} in {}", version, filePath);
		}
		JsonArray layers = root.has("buildLayers") && root.get("buildLayers").isJsonArray()
			? root.getAsJsonArray("buildLayers")
			: null;
		JsonArray groups = root.has("buildLayerGroups") && root.get("buildLayerGroups").isJsonArray()
			? root.getAsJsonArray("buildLayerGroups")
			: null;
		manager.purgeAllLayers();
		BuildLayerPersistence.loadInto(manager, layers, groups);
		return true;
	}

	private static JsonObject readRoot(Path path) throws IOException {
		String json = Files.readString(path, StandardCharsets.UTF_8);
		return JsonParser.parseString(json).getAsJsonObject();
	}

	private static Path backupPath(Path filePath) {
		return filePath.resolveSibling(filePath.getFileName() + ".bak");
	}

	private static void moveReplacing(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}
}
