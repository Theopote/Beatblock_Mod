package com.beatblock.timeline.project.migration;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Legacy {@code version: 1} → {@code version: 2}：引入 {@code buildLayers} 数组。 */
final class LegacyFormatV1ToV2Migration implements ProjectMigration {

	@Override
	public int fromVersion() {
		return 1;
	}

	@Override
	public int toVersion() {
		return 2;
	}

	@Override
	public String describeStep() {
		return "legacy format version 1 → 2";
	}

	@Override
	public JsonObject migrate(JsonObject source) {
		JsonObject root = source.deepCopy();
		if (!root.has("buildLayers") || root.get("buildLayers").isJsonNull()) {
			root.add("buildLayers", new JsonArray());
		}
		root.addProperty("version", 2);
		return root;
	}
}
