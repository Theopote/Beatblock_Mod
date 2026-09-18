package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;

/**
 * Creator schema 3 → 4：新增可选 {@code stageObjects[]}（独立 StageObject 持久化）。
 * <p>
 * 旧工程无 {@code stageObjects} 时加载行为不变；BuildLayer 内嵌 StageObject 仍为权威来源。
 */
final class CreatorSchemaV3ToV4Migration implements CreatorSchemaMigration {

	@Override
	public int fromSchema() {
		return 3;
	}

	@Override
	public int toSchema() {
		return 4;
	}

	@Override
	public String describeStep() {
		return "Creator schemaVersion 3 → 4 (standalone stageObjects)";
	}

	@Override
	public JsonObject migrate(JsonObject source) {
		JsonObject root = source.deepCopy();
		root.addProperty("schemaVersion", toSchema());
		return root;
	}
}
