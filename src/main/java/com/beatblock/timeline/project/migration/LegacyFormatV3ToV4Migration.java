package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;

/** Legacy {@code version: 3} → {@code version: 4}：引入可选 {@code choreography} 段。 */
final class LegacyFormatV3ToV4Migration implements ProjectMigration {

	@Override
	public int fromVersion() {
		return 3;
	}

	@Override
	public int toVersion() {
		return 4;
	}

	@Override
	public String describeStep() {
		return "legacy format version 3 → 4";
	}

	@Override
	public JsonObject migrate(JsonObject source) {
		JsonObject root = source.deepCopy();
		root.addProperty("version", 4);
		return root;
	}
}
