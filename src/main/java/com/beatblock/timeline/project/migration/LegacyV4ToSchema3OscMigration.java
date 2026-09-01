package com.beatblock.timeline.project.migration;

import com.google.gson.JsonObject;

/**
 * legacy {@code version: 4} → Creator Alpha {@code schemaVersion: 3}。
 * <p>
 * 数据字段不变，仅统一版本标识并写入格式名。
 */
final class LegacyV4ToSchema3OscMigration implements ProjectMigration {

	@Override
	public int fromVersion() {
		return 4;
	}

	@Override
	public int toVersion() {
		return OscSchemaVersions.CURRENT;
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
