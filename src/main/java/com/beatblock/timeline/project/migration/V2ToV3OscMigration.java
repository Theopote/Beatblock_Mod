package com.beatblock.timeline.project.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * legacy v2 → v3：引入 markers、animationTracks、durationSeconds、bpm、buildLayerGroups。
 */
final class V2ToV3OscMigration implements ProjectMigration {

	@Override
	public int fromVersion() {
		return 2;
	}

	@Override
	public int toVersion() {
		return 3;
	}

	@Override
	public JsonObject migrate(JsonObject source) {
		JsonObject root = source.deepCopy();
		ensureArray(root, "markers");
		ensureArray(root, "animationTracks");
		if (!root.has("durationSeconds")) {
			root.addProperty("durationSeconds", 0.0);
		}
		if (!root.has("bpm")) {
			root.addProperty("bpm", 0.0);
		}
		if (root.has("buildLayers") && !root.has("buildLayerGroups")) {
			root.add("buildLayerGroups", new JsonArray());
		}
		root.addProperty("version", 3);
		return root;
	}

	private static void ensureArray(JsonObject root, String key) {
		if (!root.has(key) || root.get(key).isJsonNull()) {
			root.add(key, new JsonArray());
		}
	}
}
