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
import java.nio.file.Files;
import java.nio.file.Path;

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
		Files.writeString(filePath, GSON.toJson(root), StandardCharsets.UTF_8);
	}

	public static boolean load(Path filePath, BuildLayerManager manager) throws IOException {
		if (filePath == null || manager == null || !Files.exists(filePath)) {
			return false;
		}
		String json = Files.readString(filePath, StandardCharsets.UTF_8);
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
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
}
