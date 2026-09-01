package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;

/**
 * 跨命名空间迁移：legacy {@code version: 4} → Creator {@code schemaVersion: 3}。
 * <p>
 * 数据字段不变，仅写入 {@code format} / {@code schemaVersion} 并移除 legacy {@code version}。
 * 此后工程只使用 {@code schemaVersion}，legacy {@code version} 冻结不再演进。
 */
final class LegacyFormatV4ToCreatorSchemaV3Migration implements ProjectMigration {

	@Override
	public int fromVersion() {
		return OscSchemaVersions.LEGACY_FORMAT_MAX;
	}

	@Override
	public int toVersion() {
		return OscSchemaVersions.CURRENT;
	}

	@Override
	public String describeStep() {
		return "legacy format version 4 → Creator schemaVersion " + OscSchemaVersions.CURRENT;
	}

	@Override
	public JsonObject migrate(JsonObject source) {
		JsonObject root = source.deepCopy();
		root.remove("version");
		root.addProperty("format", OscSchemaVersions.FORMAT);
		root.addProperty("schemaVersion", OscSchemaVersions.CURRENT);
		return root;
	}
}
