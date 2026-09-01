package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;

/** legacy v3 → v4：引入可选 {@code choreography} 段（缺失时由加载器容错）。 */
final class V3ToV4OscMigration implements ProjectMigration {

	@Override
	public int fromVersion() {
		return 3;
	}

	@Override
	public int toVersion() {
		return 4;
	}

	@Override
	public JsonObject migrate(JsonObject source) {
		JsonObject root = source.deepCopy();
		root.addProperty("version", 4);
		return root;
	}
}
